package com.socialhub.platform.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/**
 * Registro de arranque ({@code arranque.log}) hecho a mano, sin Spring ni Logback: si el arranque falla antes de que
 * exista el registro normal, aquí queda el motivo. Además recoge la salida estándar y de error de la JVM (en la
 * aplicación instalada no hay consola, así que se perdería). Todo pasa por {@link SecretRedactor}.
 * <p>
 * Solo actúa si se llama a {@link #init}; sin eso ({@code mvn test}, desarrollo) no escribe nada.
 */
public final class StartupLog {

    public static final String FILE_NAME = "arranque.log";
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    /** Esto corre antes que Spring: no hay un Clock inyectado al que pedirle la hora. */
    private static final Clock CLOCK = Clock.systemDefaultZone();

    private static @Nullable PrintStream sink;
    private static @Nullable Path file;

    private StartupLog() {}

    /**
     * Abre {@code <logs>/arranque.log} (si pasa de 2 MB, el anterior se queda en {@code arranque.1.log}) y le redirige
     * la salida estándar y de error. Devuelve el archivo, o null si no se puede escribir (entonces no cambia nada).
     */
    public static synchronized @Nullable Path init(Path logsDir, boolean redirectConsole) {
        if (sink != null) return file;
        try {
            Files.createDirectories(logsDir);
            Path target = logsDir.resolve(FILE_NAME);
            rotateIfBig(target, MAX_BYTES);
            PrintStream stream = new PrintStream(new FileOutputStream(target.toFile(), true), true, StandardCharsets.UTF_8);
            sink = stream;
            file = target;
            if (redirectConsole) {
                PrintStream console = new PrintStream(new RedactingLineStream(stream), true, StandardCharsets.UTF_8);
                System.setOut(console);
                System.setErr(console);
            }
            return target;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Para las pruebas: cierra el archivo (quien llamó a {@link #init} con la consola redirigida debe devolverla a su sitio). */
    public static synchronized void reset() {
        if (sink != null) sink.close();
        sink = null;
        file = null;
    }

    public static synchronized @Nullable Path file() { return file; }

    /** Una fase del arranque, con la hora. */
    public static synchronized void phase(String message) {
        write("[arranque] " + message);
    }

    /** Un fallo, con su traza completa. */
    public static synchronized void error(String message, @Nullable Throwable error) {
        StringBuilder text = new StringBuilder("[ERROR] ").append(message);
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            text.append('\n').append(trace);
        }
        write(text.toString());
    }

    private static void write(String text) {
        PrintStream stream = sink;
        if (stream == null) return;
        stream.println(OffsetDateTime.now(CLOCK).truncatedTo(ChronoUnit.MILLIS) + " " + SecretRedactor.redact(text));
    }

    /** Si el archivo pasa de {@code maxBytes}, se aparta como {@code <nombre>.1.<ext>}. No falla nunca. */
    public static void rotateIfBig(Path target, long maxBytes) {
        try {
            if (Files.exists(target) && Files.size(target) > maxBytes) {
                String name = target.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String older = dot < 0 ? name + ".1" : name.substring(0, dot) + ".1" + name.substring(dot);
                Files.move(target, target.resolveSibling(older), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            // Otro SocialHub lo tiene abierto (Windows no deja moverlo) o no hay permiso: se sigue escribiendo en él
        }
    }

    /** Pasa a un archivo lo que se escribe en la salida estándar, línea a línea y sin secretos. */
    static final class RedactingLineStream extends OutputStream {
        private static final int MAX_LINE = 64 * 1024;
        private final OutputStream target;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        RedactingLineStream(OutputStream target) { this.target = target; }

        @Override
        public synchronized void write(int b) throws IOException {
            line.write(b);
            if (b == '\n' || line.size() >= MAX_LINE) flushLine();
        }

        @Override
        public synchronized void write(byte[] bytes, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) write(bytes[i]);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (line.size() > 0 && line.toByteArray()[line.size() - 1] == '\n') flushLine();
            target.flush();
        }

        private void flushLine() throws IOException {
            String text = line.toString(StandardCharsets.UTF_8);
            line.reset();
            target.write(SecretRedactor.redact(text).getBytes(StandardCharsets.UTF_8));
            target.flush();
        }
    }
}
