package com.socialhub.production;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionEvents.TaskInterrupted;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Motor de tareas del modo real.
 *
 * <ul>
 *   <li>Toma de la cola las tareas cuyo tipo tiene ejecutor, en estaciones que aceptan trabajo
 *       ({@code SELECT … FOR UPDATE SKIP LOCKED}), y las ejecuta en hilos virtuales.</li>
 *   <li>Cada ejecución tiene límite de tiempo. Al pausar o cancelar una tarea en curso, se interrumpe.</li>
 *   <li>Al arrancar, las tareas que se quedaron «en progreso» por un cierre vuelven a la cola.</li>
 *   <li>Trabaja siempre sobre la base de datos del modo real, aunque la interfaz muestre el modo demo.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socialhub.engine.enabled", havingValue = "true", matchIfMissing = true)
public class TaskEngine {

    private final ProductionService production;
    private final Map<String, TaskExecutor> executors;
    private final int maxParallel;
    private final Map<String, Thread> running = new ConcurrentHashMap<>();
    private final Set<String> timedOut = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("socialhub-engine-timer").factory());
    private volatile boolean recovered;

    TaskEngine(ProductionService production, List<TaskExecutor> executors,
               @Value("${socialhub.engine.max-parallel:2}") int maxParallel) {
        this.production = production;
        this.executors = executors.stream().collect(Collectors.toMap(TaskExecutor::type, Function.identity()));
        this.maxParallel = Math.max(1, maxParallel);
        log.info("Motor de tareas listo: {} tipos ({}), hasta {} a la vez", this.executors.size(),
                String.join(", ", this.executors.keySet()), this.maxParallel);
    }

    /** Tipos de tarea que sabe ejecutar. */
    public Set<String> types() { return executors.keySet(); }

    public boolean isRunning(String taskId) { return running.containsKey(taskId); }

    @Scheduled(initialDelayString = "${socialhub.engine.initial-delay-millis:2000}",
               fixedDelayString = "${socialhub.engine.poll-millis:1000}")
    void poll() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                if (!recovered) {
                    recoverStale();
                    recovered = true;
                }
                int free = maxParallel - running.size();
                if (free <= 0 || executors.isEmpty()) return;
                for (ProductionService.Claimed claimed : production.claim(executors.keySet(), free)) {
                    start(claimed);
                }
            } catch (RuntimeException e) {
                log.warn("Paso del motor omitido: {}", e.toString());
            }
        });
    }

    private void start(ProductionService.Claimed claimed) {
        TaskExecutor executor = executors.get(claimed.type());
        Thread thread = Thread.ofVirtual().name("tarea-" + claimed.id()).unstarted(() -> execute(executor, claimed));
        running.put(claimed.id(), thread);
        thread.start();
        Duration limit = executor.timeout();
        timers.schedule(() -> {
            if (running.get(claimed.id()) == thread) {
                timedOut.add(claimed.id());
                thread.interrupt();
            }
        }, limit.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void execute(TaskExecutor executor, ProductionService.Claimed claimed) {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            String id = claimed.id();
            try {
                String result = executor.run(new EngineRun(claimed));
                if (Thread.interrupted()) throw new InterruptedException();
                production.finishRun(id, result);
            } catch (ResourceUnavailableException e) {
                log.info("La tarea {} espera a la carpeta {}: {}", id, e.path(), e.getMessage());
                production.blockRun(id, e.getMessage(), e.path());
            } catch (InterruptedException e) {
                if (timedOut.contains(id)) {
                    production.failRun(id, "Tiempo agotado: más de " + executor.timeout().toSeconds() + " s");
                }
                // Si no, la pausó o canceló Raúl: el estado ya está guardado
            } catch (Exception | Error e) {
                if (timedOut.contains(id)) {
                    production.failRun(id, "Tiempo agotado: más de " + executor.timeout().toSeconds() + " s");
                } else {
                    log.warn("La tarea {} ({}) falló: {}", id, claimed.type(), e.toString());
                    production.failRun(id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            } finally {
                running.remove(id);
                timedOut.remove(id);
            }
        });
    }

    /**
     * Devuelve a la cola las tareas «en progreso» que este motor no está ejecutando (se quedaron así al cerrar
     * SocialHub). Se hace al arrancar.
     */
    public int recoverStale() {
        int n = AppModeHolder.callIn(AppMode.REAL, () -> production.recoverAfterRestart(Set.copyOf(running.keySet())));
        if (n > 0) log.info("{} tareas recuperadas tras reiniciar", n);
        return n;
    }

    /** Cada 30 s: las tareas que esperaban una carpeta vuelven a la cola cuando aparece. */
    @Scheduled(initialDelayString = "${socialhub.engine.initial-delay-millis:2000}",
               fixedDelayString = "${socialhub.engine.folder-check-millis:30000}")
    void requeueWhenFoldersAppear() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                int n = production.requeueWhenAvailable(TaskEngine::exists);
                if (n > 0) log.info("{} tareas vuelven a la cola: su carpeta ya está disponible", n);
            } catch (RuntimeException e) {
                log.warn("Comprobación de carpetas omitida: {}", e.toString());
            }
        });
    }

    private static boolean exists(String path) {
        try {
            return java.nio.file.Files.exists(java.nio.file.Path.of(path));
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    /** Raúl pausa o cancela una tarea en curso: se interrumpe su ejecución. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(TaskInterrupted interrupted) {
        Thread thread = running.get(interrupted.taskId());
        if (thread != null) {
            log.info("Interrumpiendo la tarea {}", interrupted.taskId());
            thread.interrupt();
        }
    }

    private final class EngineRun implements TaskExecutor.Run {
        private final ProductionService.Claimed claimed;

        EngineRun(ProductionService.Claimed claimed) { this.claimed = claimed; }

        @Override public String taskId() { return claimed.id(); }
        @Override public String promotionId() { return claimed.promotionId(); }
        @Override public List<String> inputs() { return claimed.inputs(); }

        @Override
        public void log(String message) {
            AppModeHolder.runIn(AppMode.REAL, () -> production.logRun(claimed.id(), message));
        }

        @Override
        public void progress(double percent) {
            AppModeHolder.runIn(AppMode.REAL, () -> production.reportProgress(claimed.id(), percent));
        }
    }
}
