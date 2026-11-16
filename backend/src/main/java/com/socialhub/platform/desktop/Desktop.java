package com.socialhub.platform.desktop;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.diagnostics.JvmCrashReports;
import com.socialhub.platform.diagnostics.StartupLog;
import com.socialhub.platform.diagnostics.StartupPhases;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ayudas para SocialHub instalado. El instalador lo arranca con {@code -Dsocialhub.desktop=true}; al arrancarlo a
 * mano (desarrollo, pruebas) no se abre el navegador ni cambia nada.
 */
public final class Desktop {

    public static final int PORT = 8686;
    public static final String URL = "http://localhost:" + PORT + "/";
    private static final Logger log = LoggerFactory.getLogger(Desktop.class);

    private Desktop() {}

    public static boolean enabled() {
        return Boolean.getBoolean("socialhub.desktop");
    }

    /**
     * Antes de arrancar: el registro va a {@code <datos>/logs/socialhub.log} (no hay consola). Devuelve true si
     * SocialHub ya estaba abierto; en ese caso solo se abre el navegador.
     */
    public static boolean prepare() {
        Path logs = AppPaths.defaultDataDir().resolve("logs");
        if (System.getProperty("logging.file.name") == null) {
            System.setProperty("logging.file.name", logs.resolve("socialhub.log").toString());
        }
        // En la consola (que va a arranque.log) solo avisos y errores: el detalle ya está en socialhub.log
        if (System.getProperty("logging.threshold.console") == null) System.setProperty("logging.threshold.console", "WARN");
        if (System.getProperty("spring.main.banner-mode") == null) System.setProperty("spring.main.banner-mode", "off");
        // Registro de arranque: antes que nada, para que quede el motivo aunque Spring no llegue a iniciarse
        StartupLog.init(logs, true);
        StartupLog.phase("Arranque de SocialHub " + StartupPhases.version() + " · Java " + System.getProperty("java.version")
                + " · " + System.getProperty("os.name") + " · carpeta de datos " + AppPaths.defaultDataDir());
        StartupLog.phase("Registros de la JVM en: " + System.getProperty("socialhub.jvm.logs", "(no indicada)")
                + (System.getProperty("socialhub.jvm.logs", "").contains("$") ? "  <- AVISO: el instalador no expandió la variable" : ""));
        try {
            StartupLog.phase("Opciones de la JVM: " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        } catch (RuntimeException | LinkageError e) {
            StartupLog.phase("No se pudieron leer las opciones de la JVM: " + e);
        }
        JvmCrashReports.collect(logs, List.of(Path.of(System.getProperty("user.dir", ".")),
                Path.of(System.getProperty("java.io.tmpdir", "."))));
        rotateExternalLogs(logs);
        if (alreadyRunning()) {
            StartupLog.phase("SocialHub ya estaba abierto: solo se abre el navegador");
            openBrowser();
            return true;
        }
        if (!lockInstance()) {
            // Otro arranque va por delante (tarda un poco): se espera a que esté listo y solo se abre el navegador
            StartupLog.phase("SocialHub ya está arrancando; se espera a que esté listo");
            log.info("SocialHub ya está arrancando; se espera a que esté listo");
            for (int i = 0; i < 90 && !alreadyRunning(); i++) sleep(2000);
            StartupLog.phase("El otro SocialHub ya responde: solo se abre el navegador");
            openBrowser();
            return true;
        }
        return false;
    }

    /** PostgreSQL añade a sus registros en cada arranque y no los rota: si pasan de 20 MB se aparta el viejo (con PostgreSQL parado, al abrir). */
    private static void rotateExternalLogs(Path logs) {
        StartupLog.rotateIfBig(logs.resolve("pg_ctl.log"), 20L * 1024 * 1024);
        StartupLog.rotateIfBig(logs.resolve("postgres.log"), 20L * 1024 * 1024);
    }

    private static @Nullable FileChannel lockChannel;
    private static @Nullable FileLock lock;

    /**
     * Un solo SocialHub a la vez: el primero que arranca bloquea {@code <datos>/socialhub.lock} hasta que termina
     * (Windows lo suelta solo si el programa se cierra de golpe).
     */
    static synchronized boolean lockInstance() {
        try {
            Path file = AppPaths.defaultDataDir().resolve("socialhub.lock");
            Files.createDirectories(file.getParent());
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquired = channel.tryLock();
            if (acquired == null) {
                channel.close();
                return false;
            }
            lockChannel = channel;
            lock = acquired;
            return true;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            log.warn("No se pudo comprobar si SocialHub ya está arrancando: {}", e.getMessage());
            return true;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** ¿Responde ya un SocialHub en este ordenador? */
    static boolean alreadyRunning() {
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).proxy(HttpClient.Builder.NO_PROXY).build()) {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + "/api/app-mode"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Abre SocialHub en el navegador predeterminado (sin intérprete de órdenes). */
    public static void openBrowser() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = os.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", URL)
                : os.contains("mac") ? List.of("open", URL)
                : List.of("xdg-open", URL);
        try {
            new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            log.warn("No se pudo abrir el navegador ({}). Abre {} a mano.", e.getMessage(), URL);
        }
    }
}
