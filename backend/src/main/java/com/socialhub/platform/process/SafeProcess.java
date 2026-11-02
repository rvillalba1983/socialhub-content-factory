package com.socialhub.platform.process;

import com.socialhub.platform.observability.ExternalCalls;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Ejecuta programas locales de forma segura:
 * <ul>
 *   <li>Sin intérprete de órdenes: el programa y sus argumentos van por separado, así que nada se interpreta.</li>
 *   <li>Solo programas que SocialHub conoce (los resuelve cada herramienta, nunca un agente).</li>
 *   <li>Con límite de tiempo. Si el hilo se interrumpe (pausa o cancelación), el proceso se para.</li>
 *   <li>La salida va a un archivo temporal (una tubería llena podría bloquear el proceso) y se guarda el final.</li>
 *   <li>Cada ejecución queda en «Operaciones externas».</li>
 * </ul>
 */
@Component
public class SafeProcess {

    private static final int OUTPUT_TAIL = 4000;

    private final ExternalCalls calls;

    public SafeProcess(ExternalCalls calls) {
        this.calls = calls;
    }

    public record Result(int exitCode, String output) {
        public boolean ok() { return exitCode == 0; }
    }

    /**
     * @param system nombre del sistema para el registro, por ejemplo {@code ffmpeg}
     * @param operation operación, por ejemplo {@code encode}
     * @throws InterruptedException si se interrumpe el hilo; el proceso se para antes de lanzarla
     * @throws ProcessFailedException si termina con error o se agota el tiempo
     */
    public Result run(String system, String operation, Path executable, List<String> arguments,
                      @Nullable Path workDir, Duration timeout) throws InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        try {
            return calls.call(system, operation, String.join(" ", arguments.subList(0, Math.min(arguments.size(), 12))),
                    () -> execute(command, workDir, timeout));
        } catch (InterruptedRuntime e) {
            throw (InterruptedException) e.getCause();
        }
    }

    private Result execute(List<String> command, @Nullable Path workDir, Duration timeout) {
        Path output;
        try {
            output = Files.createTempFile("socialhub-proc-", ".log");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
            if (workDir != null) builder.directory(workDir.toFile());
            process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                throw new ProcessFailedException(name(command) + " no terminó en " + timeout.toSeconds() + " s", tail(output));
            }
            Result result = new Result(process.exitValue(), tail(output));
            if (!result.ok()) {
                throw new ProcessFailedException(name(command) + " terminó con código " + result.exitCode(), result.output());
            }
            return result;
        } catch (InterruptedException e) {
            if (process != null) stop(process);
            Thread.currentThread().interrupt();
            throw new InterruptedRuntime(e);
        } catch (IOException e) {
            throw new ProcessFailedException("No se pudo ejecutar " + name(command) + ": " + e.getMessage(), "");
        } finally {
            try { Files.deleteIfExists(output); } catch (IOException ignored) { /* temporal */ }
        }
    }

    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static String name(List<String> command) {
        return Path.of(command.getFirst()).getFileName().toString();
    }

    private static String tail(Path output) {
        try {
            String text = Files.readString(output, StandardCharsets.UTF_8);
            return text.length() <= OUTPUT_TAIL ? text : text.substring(text.length() - OUTPUT_TAIL);
        } catch (IOException e) {
            return "";
        }
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null");
    }

    /** Envoltorio para atravesar el {@code Supplier} de {@link ExternalCalls}. */
    private static final class InterruptedRuntime extends RuntimeException {
        InterruptedRuntime(InterruptedException cause) { super(cause); }
    }
}
