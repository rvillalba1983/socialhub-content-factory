package com.socialhub.platform.postgres;

import com.socialhub.platform.AppMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.socialhub.platform.observability.ExternalCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL incluido y controlado por SocialHub con pg_ctl, como en la prueba validada en Windows.
 * Solo escucha en 127.0.0.1.
 */
public class EmbeddedPostgres {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgres.class);
    public static final String USER = "socialhub";
    /** Una base de datos por modo (real y demo), en el mismo servidor. */
    public static final List<String> DATABASES = Arrays.stream(AppMode.values())
            .map(AppMode::database).toList();

    private final PostgresBinaries binaries;
    private final Path dataDir;
    private final Path logDir;
    private final int requestedPort;
    private final int timeoutSeconds;
    private final ExternalCalls calls;
    private int port;
    private volatile boolean running;

    public EmbeddedPostgres(PostgresBinaries binaries, Path dataDir, Path logDir, int port, int timeoutSeconds,
                            ExternalCalls calls) {
        this.binaries = binaries;
        this.calls = calls;
        this.dataDir = dataDir;
        this.logDir = logDir;
        this.requestedPort = port;
        this.timeoutSeconds = timeoutSeconds;
    }

    public synchronized void start() {
        if (running) return;
        calls.run("postgres", "start", dataDir.toString(), this::doStart);
    }

    private void doStart() {
        try {
            Files.createDirectories(logDir);
            if (!Files.exists(dataDir.resolve("PG_VERSION"))) {
                calls.run("postgres", "initdb", dataDir.toString(), this::initdb);
            }
            if (status()) {
                log.warn("Había un PostgreSQL de una ejecución anterior en {}; se para antes de arrancar", dataDir);
                stopQuietly();
            }
            port = requestedPort == 0 ? freePort() : requestedPort;
            ensurePortFree(port);
            run(List.of(binaries.pgCtl().toString(), "-D", dataDir.toString(),
                    "-l", logDir.resolve("postgres.log").toString(),
                    "-o", "-p " + port + " -c listen_addresses=127.0.0.1",
                    "-w", "-t", String.valueOf(timeoutSeconds), "start"), timeoutSeconds + 15);
            running = true;
            createDatabaseIfMissing();
            log.info("PostgreSQL incluido en marcha en 127.0.0.1:{} (datos: {})", port, dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo arrancar PostgreSQL", e);
        }
    }

    public synchronized void stop() {
        if (!running) return;
        calls.run("postgres", "stop", dataDir.toString(), this::stopQuietly);
        running = false;
        log.info("PostgreSQL parado");
    }

    public boolean isRunning() { return running; }
    public int port() { return port; }

    public String jdbcUrl(String database) {
        return "jdbc:postgresql://127.0.0.1:" + port + "/" + database;
    }

    /** Base de datos del modo real. */
    public String jdbcUrl() {
        return jdbcUrl(AppMode.REAL.database());
    }

    private void initdb() {
        log.info("Creando la carpeta de datos de PostgreSQL en {}", dataDir);
        try {
            Files.createDirectories(dataDir.getParent());
            run(List.of(binaries.initdb().toString(), "-D", dataDir.toString(), "-U", USER,
                    "-A", "trust", "-E", "UTF8", "--no-locale"), 180);
        } catch (IOException e) {
            throw new UncheckedIOException("initdb falló", e);
        }
    }

    private boolean status() {
        if (!Files.exists(dataDir.resolve("postmaster.pid"))) return false;
        try {
            return exec(List.of(binaries.pgCtl().toString(), "-D", dataDir.toString(), "status"), 30) == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void stopQuietly() {
        try {
            exec(List.of(binaries.pgCtl().toString(), "-D", dataDir.toString(), "-m", "fast", "-w", "-t", "60", "stop"), 75);
        } catch (IOException e) {
            log.warn("No se pudo parar PostgreSQL: {}", e.getMessage());
        }
    }

    private void createDatabaseIfMissing() {
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        try (Connection c = DriverManager.getConnection(url, USER, "");
             var check = c.prepareStatement("select 1 from pg_database where datname = ?")) {
            for (String database : DATABASES) {
                check.setString(1, database);
                if (!check.executeQuery().next()) {
                    try (var create = c.createStatement()) {
                        create.execute("create database " + database);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar la base de datos: " + e.getMessage(), e);
        }
    }

    private void run(List<String> command, long timeoutSeconds) throws IOException {
        int code = exec(command, timeoutSeconds);
        if (code != 0) {
            throw new IOException(Path.of(command.getFirst()).getFileName() + " terminó con código " + code
                    + ". Revisa " + logDir.resolve("pg_ctl.log"));
        }
    }

    /** La salida va a un archivo, nunca a una tubería: el servidor la heredaría y bloquearía la espera. */
    private int exec(List<String> command, long timeoutSeconds) throws IOException {
        Files.createDirectories(logDir);
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logDir.resolve("pg_ctl.log").toFile()));
        pb.environment().remove("PGDATA");
        Process p = pb.start();
        try {
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(Path.of(command.getFirst()).getFileName() + " no terminó en " + timeoutSeconds + " s");
            }
            return p.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido", e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void ensurePortFree(int port) {
        try (ServerSocket s = new ServerSocket(port, 1, java.net.InetAddress.getLoopbackAddress())) {
            s.setReuseAddress(true);
        } catch (IOException e) {
            throw new IllegalStateException("El puerto " + port + " ya está en uso. Cambia socialhub.postgres.port.");
        }
    }
}
