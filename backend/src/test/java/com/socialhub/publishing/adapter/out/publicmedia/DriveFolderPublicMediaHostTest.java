package com.socialhub.publishing.adapter.out.publicmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La carpeta pública de Drive: copia y conversión a JPEG, limpieza y comprobación de que el enlace devuelve una imagen. */
class DriveFolderPublicMediaHostTest {

    @TempDir Path orders;

    private final DriveFolderPublicMediaHost host = new DriveFolderPublicMediaHost("");

    @Test
    void withoutThePublicFolderThereIsNowhereToPutImages() throws Exception {
        Path jpg = image("a.jpg", "jpg");
        assertThat(host.available(orders.toString())).isFalse();
        assertThat(host.stage(orders.toString(), jpg)).isEmpty();
    }

    @Test
    void stagesAJpegAsIsAndConvertsAPngAndReleasesOnlyItsOwnFiles() throws Exception {
        Path folder = Files.createDirectory(orders.resolve("publico"));
        assertThat(host.available(orders.toString())).isTrue();
        Path jpg = image("a.jpg", "jpg");
        Path png = image("b.png", "png");

        String first = host.stage(orders.toString(), jpg).orElseThrow();
        String second = host.stage(orders.toString(), png).orElseThrow();
        assertThat(first).matches("[0-9a-f]{20}\\.jpg").isNotEqualTo(second);
        assertThat(Files.readAllBytes(folder.resolve(first))).isEqualTo(Files.readAllBytes(jpg));
        // El PNG queda como un JPEG de verdad
        assertThat(ImageIO.getImageReadersByFormatName("jpeg").hasNext()).isTrue();
        try (var in = ImageIO.createImageInputStream(folder.resolve(second).toFile())) {
            assertThat(ImageIO.getImageReaders(in).next().getFormatName().toLowerCase()).isEqualTo("jpeg");
        }

        // Un archivo que no es suyo no se toca, ni con nombres que parezcan rutas
        Path foreign = Files.writeString(folder.resolve("otro.jpg"), "x");
        host.release(orders.toString(), List.of(first, second, "otro.jpg", "../a.jpg"));
        assertThat(folder.resolve(first)).doesNotExist();
        assertThat(folder.resolve(second)).doesNotExist();
        assertThat(foreign).exists();
        assertThat(jpg).exists();
    }

    @Test
    void anImageThatCannotBeConvertedIsRejected() throws Exception {
        Files.createDirectory(orders.resolve("publico"));
        Path fake = Files.writeString(orders.resolve("x.webp"), "no es una imagen");
        assertThatThrownBy(() -> host.stage(orders.toString(), fake)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG o PNG");
    }

    @Test
    void aLinkMustAnswerWithAnImage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> reply(ex, 200, "image/jpeg"));
        server.createContext("/html", ex -> reply(ex, 200, "text/html"));
        server.createContext("/gone", ex -> reply(ex, 404, "image/jpeg"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(host.problem(URI.create(base + "/ok"))).isEmpty();
            assertThat(host.problem(URI.create(base + "/html"))).get().asString().contains("no devuelve una imagen");
            assertThat(host.problem(URI.create(base + "/gone"))).get().asString().contains("404");
        } finally {
            server.stop(0);
        }
        assertThat(host.problem(URI.create("http://127.0.0.1:1/x"))).isPresent();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String type) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, 3);
        ex.getResponseBody().write("abc".getBytes());
        ex.close();
    }

    private Path image(String name, String format) throws IOException {
        Path file = orders.resolve(name);
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB), format.equals("jpg") ? "png" : format, file.toFile());
        if (format.equals("jpg")) {
            ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
        }
        return file;
    }
}
