package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Flujo «Cerrar SocialHub» (manual, §1): el botón llama a {@code POST /api/app/shutdown}, que acaba con
 * {@code System.exit}. Eso no se puede probar dentro de la JVM de las pruebas, así que aquí SocialHub arranca como lo
 * hace el instalador: un proceso aparte, con su carpeta de datos y su PostgreSQL. Se comprueba que la orden exige la
 * cabecera de protección, que responde antes de cerrarse, que el proceso termina bien, que PostgreSQL se para y que al
 * volver a abrirlo sigue todo lo que había.
 */
class ShutdownFlowTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @TempDir Path data;

    @Test
    void closingFromTheWebStopsTheEngineTheDatabaseAndTheServerAndEverythingIsThereWhenItOpensAgain() throws Exception {
        Process first = start(port());
        int firstPort = lastPort;
        try {
            waitUntilUp(firstPort, first);
            // Algo a medias: una marca, una promoción y una publicación en producción
            assertThat(send("POST", firstPort, "/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}", true).statusCode()).isEqualTo(201);
            assertThat(send("POST", firstPort, "/api/promotions", """
                    {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-25","status":"ACTIVE"}""", true).statusCode()).isEqualTo(201);
            assertThat(send("POST", firstPort, "/api/promotions/poemario/publications", """
                    {"platform":"INSTAGRAM","type":"Carrusel","title":"A medias","scheduled_on":"2027-01-10",
                     "scheduled_time":"19:00","initial_status":"IN_PRODUCTION"}""", true).statusCode()).isEqualTo(201);
            assertThat(Files.exists(data.resolve("pgdata/postmaster.pid"))).as("PostgreSQL en marcha").isTrue();

            // Sin la cabecera de protección nadie (otra web abierta en el navegador) puede cerrarlo
            assertThat(send("POST", firstPort, "/api/app/shutdown", "", false).statusCode()).isEqualTo(403);
            assertThat(first.isAlive()).isTrue();
            assertThat(send("GET", firstPort, "/api/app-mode", null, false).statusCode()).isEqualTo(200);

            // La orden responde primero y se cierra después
            assertThat(send("POST", firstPort, "/api/app/shutdown", "", true).statusCode()).isEqualTo(202);
            assertThat(first.waitFor(60, TimeUnit.SECONDS)).as("el programa termina solo").isTrue();
            assertThat(first.exitValue()).as("sale sin error").isZero();
            assertThat(Files.exists(data.resolve("pgdata/postmaster.pid"))).as("PostgreSQL parado").isFalse();
            assertThat(answers(firstPort)).as("el servidor ya no escucha").isFalse();
        } finally {
            first.destroyForcibly();
        }

        // Al abrir otra vez sigue lo que había
        Process second = start(port());
        int secondPort = lastPort;
        try {
            waitUntilUp(secondPort, second);
            JsonNode publications = json.readTree(send("GET", secondPort, "/api/promotions/poemario/publications", null, false).body());
            assertThat(publications.get("total").asInt()).isEqualTo(1);
            assertThat(publications.get("items").get(0).get("title").asString()).isEqualTo("A medias");
            assertThat(publications.get("items").get(0).get("status").asString()).isEqualTo("IN_PRODUCTION");
            assertThat(send("POST", secondPort, "/api/app/shutdown", "", true).statusCode()).isEqualTo(202);
            assertThat(second.waitFor(60, TimeUnit.SECONDS)).isTrue();
            assertThat(second.exitValue()).isZero();
        } finally {
            second.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------ Proceso

    private int lastPort;
    private int launches;

    private int port() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** SocialHub en un proceso aparte con la misma ruta de clases que esta prueba, como lo arranca el instalador. */
    private Process start(int port) throws IOException {
        lastPort = port;
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-cp", System.getProperty("java.class.path"),
                "com.socialhub.SocialHubApplication",
                "--server.port=" + port,
                "--socialhub.data-dir=" + data,
                "--socialhub.postgres.port=0",
                "--socialhub.postgres.runtime-dir=" + Path.of("target/pg-runtime").toAbsolutePath(),
                "--socialhub.default-mode=REAL",
                "--socialhub.demo.simulate=false"));
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(data.resolve("salida-" + (++launches) + ".log").toFile()).start();
    }

    private void waitUntilUp(int port, Process process) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (!answers(port)) {
            assertThat(process.isAlive()).as("SocialHub arranca; salida:%n%s", output()).isTrue();
            assertThat(System.currentTimeMillis()).as("SocialHub tarda demasiado en arrancar; salida:%n%s", output()).isLessThan(deadline);
            Thread.sleep(500);
        }
    }

    private String output() {
        try {
            return Files.readString(data.resolve("salida-" + launches + ".log"));
        } catch (IOException e) {
            return "(sin salida)";
        }
    }

    private boolean answers(int port) {
        try {
            return send("GET", port, "/api/app-mode", null, false).statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private HttpResponse<String> send(String method, int port, String path, String body, boolean guard)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        if (guard) request.header("X-SocialHub-Request", "1");
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
