package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Flujo «Métricas a los 7 días con fuente» (manual, §4), de extremo a extremo y con los relojes del motor y de las
 * métricas en marcha por su cuenta: dos publicaciones llegan a «Publicada» por la vía de Publer (Claude, con archivos de
 * resultado) → no se piden antes de 7 días → a los 7 días sale UN encargo «Leer métricas» con las dos → el resultado deja
 * «Sin dato» donde Claude no vio la cifra → los totales de la promoción solo cuentan lo medido → no se vuelven a pedir.
 */
@FlowTest
@TestPropertySource(properties = {
        "socialhub.publishing.initial-delay-millis=200",
        "socialhub.publishing.tick-millis=300",
        "socialhub.metrics.initial-delay-millis=200",
        "socialhub.metrics.tick-millis=300"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetricsFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path approved;
    static Path story;
    static String a;
    static String b;
    static Instant publishedAt;

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow;

    private Flow flow() {
        if (flow == null) flow = new Flow(mvc, clock, orders);
        return flow;
    }

    @Test
    @Order(1)
    void twoPublicationsReachPublishedThroughPubler() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        approved = Files.createDirectories(drive.resolve("DeLito/aprobados"));
        BufferedImage image = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(178, 34, 34));
        g.fillRect(0, 0, 1080, 1920);
        g.dispose();
        story = approved.resolve("historia.jpg");
        ImageIO.write(image, "jpg", story.toFile());

        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"APPROVED\",\"location\":" + f.q(approved) + "}]}"));
        f.ok(f.put("/api/ai/settings", "{\"daily_limit\":100,\"models\":[]}"));
        f.ok(f.put("/api/brands/el-delito/publer-accounts", "{\"items\":[{\"platform\":\"INSTAGRAM\",\"account_name\":\"El DeLito\"}]}"));

        a = story("09:00");
        b = story("10:00");
        for (String id : List.of(a, b)) {
            f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"SCHEDULED\"}"));
            String task = f.job(id).get("task_id").asString();
            f.waitFor(() -> "WAITING".equals(f.task(task).get("status").asString()), "encargo de Publer enviado");
            Instant at = LocalDateTime.of(2026, 11, 12, id.equals(a) ? 9 : 10, 0).atZone(FlowClock.MADRID).toInstant();
            f.result(task, Flow.completed(task, "{\"scheduled\":true,\"scheduled_for\":\"" + at + "\"}"));
            f.waitFor(() -> "SCHEDULED".equals(f.job(id).get("status").asString()), "programada");
        }

        // Pasadas las horas, la comprobación de cada una (Claude con Chrome) las deja publicadas con su enlace
        clock.set(LocalDateTime.of(2026, 11, 12, 10, 30).atZone(FlowClock.MADRID).toInstant());
        for (String id : List.of(a, b)) {
            f.waitFor(() -> "CHECKING".equals(f.job(id).get("status").asString()) && f.job(id).hasNonNull("task_id"), "comprobación pedida");
            String check = f.job(id).get("task_id").asString();
            f.waitFor(() -> "WAITING".equals(f.task(check).get("status").asString()), "comprobación enviada");
            f.complete(check, "{\"published\":true,\"url\":\"https://www.instagram.com/stories/elde_lito/" + id.hashCode() + "\"}");
            f.waitFor(() -> "PUBLISHED".equals(f.status("poemario", id)), "publicada");
        }
        publishedAt = clock.instant();
        assertThat(f.requestsOfType("para-claude-pc", "socialhub.metrics-read")).isEmpty();
    }

    @Test
    @Order(2)
    void nothingIsAskedBeforeSevenDaysAndThenOneOrderCarriesBothPublications() throws Exception {
        Flow f = flow();
        clock.set(publishedAt.plus(Duration.ofDays(6)));
        Thread.sleep(1_200); // varias vueltas del reloj de las métricas
        assertThat(f.requestsOfType("para-claude-pc", "socialhub.metrics-read")).isEmpty();

        clock.set(publishedAt.plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        f.waitFor(() -> f.requestsOfType("para-claude-pc", "socialhub.metrics-read").size() == 1, "encargo de métricas");
        Thread.sleep(1_000); // y una sola vez
        List<JsonNode> sent = f.requestsOfType("para-claude-pc", "socialhub.metrics-read");
        assertThat(sent).hasSize(1);
        JsonNode publications = sent.getFirst().get("inputs").get("publications");
        assertThat(publications).hasSize(2);
        assertThat(publications.get(0).get("url").asString()).contains("instagram.com/stories");
        assertThat(f.read("/api/publications/" + a + "/metrics").get("status").asString()).isEqualTo("REQUESTED");
        assertThat(f.read("/api/publications/" + b + "/metrics").get("status").asString()).isEqualTo("REQUESTED");
        // Pedidas pero sin leer: cuentan como pendientes, no como medidas
        JsonNode totals = f.read("/api/promotions/poemario/metrics");
        assertThat(totals.get("measured").asInt()).isZero();
        assertThat(totals.get("pending").asInt()).isEqualTo(2);
    }

    @Test
    @Order(3)
    void theResultKeepsNoDataWhereClaudeSawNothingAndTheTotalsOnlyCountWhatWasMeasured() throws Exception {
        Flow f = flow();
        String task = f.requestsOfType("para-claude-pc", "socialhub.metrics-read").getFirst().get("taskId").asString();
        f.complete(task, """
                {"metrics":[
                  {"publication_id":"%s","views":1000,"reach":800,"likes":60,"comments":5,"saves":9,"shares":3},
                  {"publication_id":"%s","views":null,"reach":null,"likes":10,"comments":2,"saves":null,"shares":1,
                   "note":"Historia: sin reproducciones ni alcance a la vista"}],"summary":"ok"}""".formatted(a, b));
        assertThat(f.task(task).get("result").asString()).startsWith("Métricas guardadas de 2 publicaciones");

        JsonNode ma = f.read("/api/publications/" + a + "/metrics");
        assertThat(ma.get("status").asString()).isEqualTo("DONE");
        assertThat(ma.get("views").asLong()).isEqualTo(1000);
        JsonNode mb = f.read("/api/publications/" + b + "/metrics");
        assertThat(mb.get("status").asString()).isEqualTo("DONE");
        assertThat(mb.hasNonNull("views")).isFalse();
        assertThat(mb.hasNonNull("reach")).isFalse();
        assertThat(mb.hasNonNull("saves")).isFalse();
        assertThat(mb.get("likes").asLong()).isEqualTo(10);
        assertThat(mb.get("note").asString()).startsWith("Historia: sin reproducciones");

        JsonNode totals = f.read("/api/promotions/poemario/metrics");
        assertThat(totals.get("measured").asInt()).isEqualTo(2);
        assertThat(totals.get("pending").asInt()).isZero();
        JsonNode total = network(totals, "TOTAL");
        assertThat(total.get("publications").asInt()).isEqualTo(2);
        // «Sin dato» no suma cero: solo cuenta lo que sí se vio
        assertThat(total.get("views").asLong()).isEqualTo(1000);
        assertThat(total.get("reach").asLong()).isEqualTo(800);
        assertThat(total.get("likes").asLong()).isEqualTo(70);
        assertThat(total.get("comments").asLong()).isEqualTo(7);
        assertThat(total.get("saves").asLong()).isEqualTo(9);
        assertThat(total.get("shares").asLong()).isEqualTo(4);
        assertThat(network(totals, "INSTAGRAM").get("likes").asLong()).isEqualTo(70);
        // La media por formato divide solo entre las que tienen la cifra; con 2 publicaciones es muestra baja
        JsonNode format = totals.get("by_format").get(0);
        assertThat(format.get("type").asString()).isEqualTo("Historia");
        assertThat(format.get("publications").asInt()).isEqualTo(2);
        assertThat(format.get("low_sample").asBoolean()).isTrue();
        assertThat(format.get("views").asDouble()).isEqualTo(1000.0);
        assertThat(format.get("likes").asDouble()).isEqualTo(35.0);
    }

    @Test
    @Order(4)
    void onceMeasuredTheyAreNotAskedAgain() throws Exception {
        Flow f = flow();
        clock.set(publishedAt.plus(Duration.ofDays(10)));
        Thread.sleep(1_500);
        assertThat(f.requestsOfType("para-claude-pc", "socialhub.metrics-read")).hasSize(1);
        clock.reset();
    }

    // ---------------------------------------------------------------- utilidades

    private String story(String time) throws Exception {
        Flow f = flow();
        JsonNode created = f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Historia","title":"Historia %s","scheduled_on":"2026-11-12","scheduled_time":"%s",
                 "initial_status":"IN_PRODUCTION"}""".formatted(time, time)));
        String id = created.get("id").asString();
        f.ok(f.post("/api/publications/" + id + "/files", "{\"path\":" + f.q(story) + "}"));
        for (String s : List.of("DRAFT", "IN_REVIEW", "APPROVED")) {
            f.ok(f.post("/api/publications/" + id + "/status-changes", "{\"status\":\"" + s + "\"}"));
        }
        return id;
    }

    private static JsonNode network(JsonNode totals, String name) {
        for (JsonNode n : totals.get("by_network")) {
            if (n.get("network").asString().equals(name)) return n;
        }
        throw new AssertionError("Sin fila " + name + ": " + totals.get("by_network"));
    }
}
