package com.socialhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.production.ProductionQueries;
import com.socialhub.production.ProductionService;
import com.socialhub.production.TaskEngine;
import com.socialhub.production.TaskExecutor;
import com.socialhub.production.TaskRequest;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskCommandType;
import com.socialhub.production.domain.TaskHistoryEntry;
import com.socialhub.production.domain.TaskStatus;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import com.socialhub.platform.postgres.ModeDataSources;

/** Motor de tareas del modo real con ejecutores de prueba, y comprobación real de las herramientas locales. */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.engine.poll-millis=100",
        "socialhub.engine.initial-delay-millis=100"
})
@Import(TaskEngineTest.TestExecutors.class)
class TaskEngineTest {

    @Autowired ProductionService production;
    @Autowired ProductionQueries queries;
    @Autowired ModeDataSources databases;
    @Autowired TaskEngine engine;

    @TestConfiguration
    static class TestExecutors {
        @Bean TaskExecutor ok() { return executor("TEST_OK", Duration.ofSeconds(30), run -> {
            run.log("Paso 1 hecho");
            run.progress(50);
            return "Hecho con " + run.inputs();
        }); }

        @Bean TaskExecutor failing() { return executor("TEST_FAIL", Duration.ofSeconds(30), run -> {
            throw new IllegalStateException("La carpeta de entrada no existe");
        }); }

        @Bean TaskExecutor slow() { return executor("TEST_SLOW", Duration.ofMillis(500), run -> {
            Thread.sleep(30_000);
            return "nunca";
        }); }

        @Bean TaskExecutor waiting() { return executor("TEST_WAIT", Duration.ofSeconds(60), run -> {
            Thread.sleep(30_000);
            return "nunca";
        }); }

        interface Body { String run(TaskExecutor.Run run) throws Exception; }

        static TaskExecutor executor(String type, Duration timeout, Body body) {
            return new TaskExecutor() {
                @Override public String type() { return type; }
                @Override public String agentName() { return "Prueba " + type; }
                @Override public Duration timeout() { return timeout; }
                @Override public String run(Run run) throws Exception { return body.run(run); }
            };
        }
    }

    @Test
    void runsQueuedTasksAndRecordsTheExecution() {
        Task task = submit("TEST_OK", List.of("spec.json"), List.of());
        Task done = waitFor(task.getId(), TaskStatus.COMPLETED);
        assertThat(done.getResult()).isEqualTo("Hecho con [spec.json]");
        assertThat(done.getAgent().name()).isEqualTo("Prueba TEST_OK");
        assertThat(done.getHistory()).extracting(TaskHistoryEntry::message)
                .contains("Creada", "En ejecución", "Paso 1 hecho", "Completada");
    }

    @Test
    void aFailureIsRecordedAndCanBeRetriedOnce() {
        Task task = submit("TEST_FAIL", List.of(), List.of());
        Task failed = waitFor(task.getId(), TaskStatus.FAILED);
        assertThat(failed.getError()).isEqualTo("La carpeta de entrada no existe");
        real(() -> production.execute(task.getId(), TaskCommandType.RETRY, null));
        Task again = waitFor(task.getId(), TaskStatus.FAILED);
        assertThat(again.getAttempts()).isEqualTo(2);
        assertThat(again.allowedCommands()).doesNotContain(TaskCommandType.RETRY).contains(TaskCommandType.SWITCH_TO_MANUAL);
    }

    @Test
    void anExecutionThatTakesTooLongIsStopped() {
        Task task = submit("TEST_SLOW", List.of(), List.of());
        Task failed = waitFor(task.getId(), TaskStatus.FAILED);
        assertThat(failed.getError()).startsWith("Tiempo agotado");
    }

    @Test
    void cancellingARunningTaskStopsIt() {
        Task task = submit("TEST_WAIT", List.of(), List.of());
        waitFor(task.getId(), TaskStatus.RUNNING);
        real(() -> production.execute(task.getId(), TaskCommandType.CANCEL, null));
        assertThat(waitFor(task.getId(), TaskStatus.CANCELLED).getReason()).isEqualTo("Cancelada por ti.");
    }

    @Test
    void dependentTasksWaitUntilTheirDependenciesFinish() {
        Task first = submit("TEST_OK", List.of("a"), List.of());
        Task second = submit("TEST_OK", List.of("b"), List.of(first.getId()));
        assertThat(second.getStatus()).isIn(TaskStatus.BLOCKED, TaskStatus.QUEUED);
        waitFor(second.getId(), TaskStatus.COMPLETED);
        assertThat(get(first.getId()).getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void tasksLeftRunningByARestartGoBackToTheQueue() {
        // Simula una tarea que quedó «en progreso» al cerrar SocialHub (sin ejecutor que la mueva)
        Task task = submit("TEST_UNKNOWN", List.of(), List.of());
        JdbcClient.create(databases.of(AppMode.REAL))
                .sql("update task set status = 'RUNNING' where id = ?").param(task.getId()).update();
        assertThat(engine.recoverStale()).isGreaterThanOrEqualTo(1);
        assertThat(get(task.getId()).getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void theLocalToolsAreCheckedAtStartupAndShownInTheArmory() {
        Task check = waitFor("check-local-tools", TaskStatus.COMPLETED);
        assertThat(check.getResult()).isEqualTo("2 herramientas locales funcionan.");
        assertThat(check.getHistory()).extracting(TaskHistoryEntry::message)
                .anyMatch(m -> m.matches("OK · FFmpeg: ffmpeg version [0-9.]+"))
                .anyMatch(m -> m.startsWith("OK · Imágenes (Java 2D)"));
        var tools = JdbcClient.create(databases.of(AppMode.REAL))
                .sql("select id from armory_item where kind = 'TOOL' and status = 'OK' order by id").query(String.class).list();
        assertThat(tools).containsExactly("ffmpeg", "java2d");
    }

    private Task submit(String type, List<String> inputs, List<String> dependsOn) {
        return real(() -> production.submit(new TaskRequest("gen", null, type, "Prueba " + type, "Probar el motor",
                inputs, dependsOn), "Prueba " + type));
    }

    private Task get(String id) {
        return real(() -> queries.task(id));
    }

    private Task waitFor(String id, TaskStatus status) {
        long deadline = System.currentTimeMillis() + 60_000;
        Task task = get(id);
        while (task.getStatus() != status && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            task = get(id);
        }
        assertThat(task.getStatus()).as("estado de %s (error: %s)", id, task.getError()).isEqualTo(status);
        return task;
    }

    private static <T> T real(Supplier<T> action) {
        return AppModeHolder.callIn(AppMode.REAL, action);
    }
}
