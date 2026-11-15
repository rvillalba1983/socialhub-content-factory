package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Herramientas comunes de las pruebas de flujo: llamadas a la API como lo haría la interfaz web (con la cabecera de
 * protección en las escrituras) y el intercambio con Claude por la carpeta de Encargos de Drive (encargos en
 * {@code para-claude} y {@code para-claude-pc}, resultados en {@code de-claude}).
 */
public final class Flow {

    public static final String GUARD = "X-SocialHub-Request";

    public final MockMvc mvc;
    public final Clock clock;
    public final JsonMapper json = JsonMapper.builder().build();
    /** Carpeta «Encargos» de la marca; la prueba la da al configurarla. */
    public final Path orders;

    public Flow(MockMvc mvc, Clock clock, Path orders) {
        this.mvc = mvc;
        this.clock = clock;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ API

    public ResultActions get(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url));
    }

    public JsonNode read(String url) throws Exception {
        String body = get(url).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    public ResultActions write(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header(GUARD, "1").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    public ResultActions post(String url, String body) throws Exception {
        return write(MockMvcRequestBuilders.post(url), body);
    }

    public ResultActions put(String url, String body) throws Exception {
        return write(MockMvcRequestBuilders.put(url), body);
    }

    public ResultActions delete(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(url).header(GUARD, "1"));
    }

    /** Escribe y devuelve el cuerpo ya leído; la llamada tiene que acabar bien (2xx). */
    public JsonNode ok(ResultActions call) throws Exception {
        var response = call.andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isBetween(200, 299);
        String body = response.getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    public String q(Object text) {
        return quote(text.toString());
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Un texto como cadena JSON, con sus comillas y escapes. */
    public static String quote(String text) {
        return JSON.writeValueAsString(text);
    }

    // ------------------------------------------------------------------ Lo que se ve

    public JsonNode task(String id) throws Exception { return read("/api/tasks/" + id); }

    public JsonNode publication(String promotionId, String id) throws Exception {
        for (JsonNode p : read("/api/promotions/" + promotionId + "/publications?limit=500").get("items")) {
            if (p.get("id").asString().equals(id)) return p;
        }
        throw new AssertionError("No existe la publicación " + id);
    }

    public String status(String promotionId, String id) throws Exception {
        return publication(promotionId, id).get("status").asString();
    }

    public JsonNode job(String publicationId) throws Exception { return read("/api/publications/" + publicationId + "/publish-job"); }

    public List<JsonNode> alerts() throws Exception {
        List<JsonNode> list = new ArrayList<>();
        read("/api/factory").get("alerts").forEach(list::add);
        return list;
    }

    public boolean hasAlert(String targetId) throws Exception {
        return alerts().stream().anyMatch(a -> a.get("target_id").asString().equals(targetId));
    }

    // ------------------------------------------------------------------ Claude

    /** Pide un encargo a mano, espera a que salga a {@code para-claude} y devuelve su tarea. */
    public String order(String promotionId, String type, String goal, String... inputs) throws Exception {
        List<String> quoted = new ArrayList<>();
        for (String input : inputs) quoted.add(q(input));
        JsonNode created = ok(post("/api/promotions/" + promotionId + "/ai-orders",
                "{\"type\":\"%s\",\"goal\":%s,\"inputs\":[%s]}".formatted(type, q(goal), String.join(",", quoted))));
        String id = created.get("task_id").asString();
        waitFor(() -> "WAITING".equals(task(id).get("status").asString()), "encargo " + type + " enviado");
        return id;
    }

    /** El último archivo de encargo de esa tarea en {@code para-claude} (o en la carpeta que se diga). */
    public JsonNode request(String taskId, String folder) throws Exception {
        Path file = list(orders.resolve(folder)).stream()
                .filter(p -> p.getFileName().toString().startsWith(taskId)).reduce((a, b) -> b).orElseThrow(
                        () -> new AssertionError("No hay encargo de " + taskId + " en " + folder));
        return json.readTree(Files.readString(file));
    }

    public JsonNode request(String taskId) throws Exception { return request(taskId, "para-claude"); }

    public JsonNode pcRequest(String taskId) throws Exception { return request(taskId, "para-claude-pc"); }

    /** Los encargos de un tipo que hay en una carpeta de salida (por el campo {@code type} del archivo). */
    public List<JsonNode> requestsOfType(String folder, String type) throws Exception {
        Path dir = orders.resolve(folder);
        if (!Files.isDirectory(dir)) return List.of();
        List<JsonNode> found = new ArrayList<>();
        for (Path file : list(dir)) {
            if (!file.getFileName().toString().endsWith(".json")) continue;
            JsonNode node = json.readTree(Files.readString(file));
            if (type.equals(node.path("type").asString(""))) found.add(node);
        }
        return found;
    }

    /** Lo que escribe la tarea programada de Claude al acabar: un resultado correcto con esos datos de salida. */
    public static String completed(String taskId, String outputs) {
        return """
                {"contract":"socialhub.task-result","version":1,"taskId":"%s","status":"completed","outputs":%s,
                 "agent":{"runtime":"tarea programada en la nube","model":"sonnet"}}""".formatted(taskId, outputs);
    }

    /** Deja el archivo de resultado en {@code de-claude}, con una hora de modificación que SocialHub da ya por asentada. */
    public void result(String taskId, String content) throws Exception {
        Path file = Files.createDirectories(orders.resolve("de-claude")).resolve(taskId + "-" + System.nanoTime() + ".json");
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(clock.instant().minusSeconds(30)));
    }

    /** Resultado correcto de Claude y espera a que SocialHub lo aplique (la tarea termina bien). */
    public void complete(String taskId, String outputs) throws Exception {
        result(taskId, completed(taskId, outputs));
        waitFor(() -> "COMPLETED".equals(task(taskId).get("status").asString()), "resultado de " + taskId + " aplicado");
    }

    // ------------------------------------------------------------------ Utilidades

    public static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        }
    }

    public void waitFor(Callable<Boolean> condition, String what) throws Exception {
        waitFor(condition, what, 30);
    }

    public void waitFor(Callable<Boolean> condition, String what, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (!condition.call()) {
            assertThat(System.currentTimeMillis()).as("Esperando: " + what).isLessThan(deadline);
            Thread.sleep(150);
        }
    }
}
