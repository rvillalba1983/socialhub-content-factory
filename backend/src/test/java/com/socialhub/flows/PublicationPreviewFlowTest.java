package com.socialhub.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Vista previa: el endpoint de contenido solo entrega archivos asociados a esa publicación y dentro de las carpetas de su
 * promoción, con su tipo MIME y con Range para el vídeo.
 */
@FlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublicationPreviewFlowTest {

    @TempDir static Path drive;
    static Path orders;
    static Path approved;
    static String one;
    static String two;
    static String imageId;
    static String videoId;
    static String textId;
    static String otherId;
    static final byte[] VIDEO = "0123456789abcdefghijklmnopqrstuvwxyz".repeat(10).getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired FlowClock clock;

    private Flow flow() { return new Flow(mvc, clock, orders); }

    private String url(String publication, String fileId) { return "/api/publications/" + publication + "/files/" + fileId + "/content"; }

    @Test
    @Order(1)
    void setUp() throws Exception {
        orders = Files.createDirectories(drive.resolve("DeLito/encargos"));
        approved = Files.createDirectories(drive.resolve("DeLito/aprobados"));
        Files.write(approved.resolve("foto.jpg"), new byte[] {1, 2, 3, 4});
        Files.write(approved.resolve("clip.mp4"), VIDEO);
        Files.writeString(approved.resolve("notas.txt"), "secreto");
        Files.write(approved.resolve("otra.png"), new byte[] {9, 9});
        Flow f = flow();
        f.ok(f.post("/api/brands", "{\"name\":\"El DeLito\",\"handle\":\"@elde_lito\",\"color\":\"#D23A2E\"}"));
        f.ok(f.post("/api/promotions", """
                {"brand_id":"el-delito","name":"Poemario","starts_on":"2026-11-03","ends_on":"2027-01-31","status":"ACTIVE"}"""));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"APPROVED\",\"location\":" + f.q(approved) + "}]}"));
        one = publication(f, "09:00");
        two = publication(f, "10:00");
        imageId = attach(f, one, approved.resolve("foto.jpg"));
        videoId = attach(f, one, approved.resolve("clip.mp4"));
        textId = attach(f, one, approved.resolve("notas.txt"));
        otherId = attach(f, two, approved.resolve("otra.png"));
    }

    @Test
    @Order(2)
    void listingExposesOpaqueIdsKindsAndMimeTypes() throws Exception {
        JsonNode items = flow().read("/api/publications/" + one + "/files").get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("kind").asString()).isEqualTo("IMAGE");
        assertThat(items.get(0).get("mime_type").asString()).isEqualTo("image/jpeg");
        assertThat(items.get(1).get("kind").asString()).isEqualTo("VIDEO");
        assertThat(items.get(2).get("kind").asString()).isEqualTo("OTHER");
        assertThat(items.get(2).hasNonNull("mime_type")).isFalse();
        assertThat(imageId).matches("[0-9a-f]{16}").doesNotContain("foto");
    }

    @Test
    @Order(3)
    void servesAnImageWithItsMimeType() throws Exception {
        var response = mvc.perform(get(url(one, imageId))).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @Order(4)
    void servesVideoInPartsWithRange() throws Exception {
        var full = mvc.perform(get(url(one, videoId))).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(full.getContentType()).isEqualTo("video/mp4");
        assertThat(full.getHeader("Accept-Ranges")).isEqualTo("bytes");
        var part = mvc.perform(get(url(one, videoId)).header("Range", "bytes=10-19")).andExpect(status().isPartialContent())
                .andReturn().getResponse();
        assertThat(part.getContentAsByteArray()).containsExactly(java.util.Arrays.copyOfRange(VIDEO, 10, 20));
        assertThat(part.getHeader("Content-Range")).isEqualTo("bytes 10-19/" + VIDEO.length);
        mvc.perform(get(url(one, videoId)).header("Range", "bytes=99999-")).andExpect(status().isRequestedRangeNotSatisfiable());
    }

    @Test
    @Order(5)
    void aFileOfAnotherPublicationIsNotFound() throws Exception {
        mvc.perform(get(url(one, otherId))).andExpect(status().isNotFound());
        mvc.perform(get(url(two, imageId))).andExpect(status().isNotFound());
        mvc.perform(get(url("no-existe", imageId))).andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void anInventedIdOrAPathIsRejected() throws Exception {
        mvc.perform(get(url(one, "0123456789abcdef"))).andExpect(status().isNotFound());
        for (String bad : new String[] {"..", "../../etc/passwd", "..%2F..%2Fetc%2Fpasswd", "%2e%2e%2f%2e%2e%2fsecret",
                "C:%5CWindows%5Cwin.ini", approved.resolve("foto.jpg").toString().replace("/", "%2F").replace("\\", "%5C")}) {
            int code = mvc.perform(get("/api/publications/" + one + "/files/" + bad + "/content")).andReturn().getResponse().getStatus();
            assertThat(code).as(bad).isBetween(400, 404);
        }
    }

    @Test
    @Order(7)
    void aFileThatIsNotAnImageOrVideoIsNotServed() throws Exception {
        mvc.perform(get(url(one, textId))).andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    void aFileOutsideThePromotionFoldersIsNotServedEvenIfItWasAssociated() throws Exception {
        Flow f = flow();
        Path elsewhere = Files.createDirectories(drive.resolve("OtraCosa"));
        f.ok(f.put("/api/brands/el-delito/folders", "{\"items\":[{\"role\":\"ORDERS\",\"location\":" + f.q(orders)
                + "},{\"role\":\"APPROVED\",\"location\":" + f.q(elsewhere) + "}]}"));
        mvc.perform(get(url(one, imageId))).andExpect(status().isNotFound());
    }

    private String publication(Flow f, String time) throws Exception {
        return f.ok(f.post("/api/promotions/poemario/publications", """
                {"platform":"INSTAGRAM","type":"Historia","title":"Historia %s","scheduled_on":"2026-11-12","scheduled_time":"%s",
                 "initial_status":"IN_PRODUCTION"}""".formatted(time, time))).get("id").asString();
    }

    private String attach(Flow f, String publication, Path file) throws Exception {
        return f.ok(f.post("/api/publications/" + publication + "/files", "{\"path\":" + f.q(file) + "}")).get("id").asString();
    }
}
