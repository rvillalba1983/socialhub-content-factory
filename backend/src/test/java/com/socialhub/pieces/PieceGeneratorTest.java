package com.socialhub.pieces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.platform.process.SafeProcess;
import com.socialhub.tools.FfmpegTool;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Las piezas de promo.py generadas en Java con FFmpeg real, sobre imágenes, audio y vídeo de prueba. */
class PieceGeneratorTest {

    @TempDir static Path data;
    @TempDir static Path drive;

    static FfmpegTool ffmpeg;
    static PieceGenerator generator;
    static SpecReader reader;

    @BeforeAll
    static void prepare() throws Exception {
        ffmpeg = new FfmpegTool(new SafeProcess(ExternalCalls.unobserved()),
                new AppPaths(new SocialHubProperties(data.toString(), null, null, null)));
        generator = new PieceGenerator(ffmpeg);
        reader = new SpecReader(JsonMapper.builder().build(), generator);
        Path media = Files.createDirectories(drive.resolve("DeLito/material"));
        image(media.resolve("page 46.png"), 1600, 1200, new Color(180, 60, 40), new Color(250, 230, 200));
        image(media.resolve("page 04.jpg"), 1000, 1500, new Color(30, 60, 120), new Color(240, 240, 240));
        ff("-f", "lavfi", "-i", "sine=frequency=440:duration=40", "-c:a", "aac", "-b:a", "128k", media.resolve("15 pua.m4a").toString());
        ff("-f", "lavfi", "-i", "testsrc=size=1920x1080:rate=30:duration=8", "-f", "lavfi", "-i", "sine=frequency=330:duration=8",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", media.resolve("video.mp4").toString());
        // Más de 9,5 MB: 10 s de ruido a 20 Mbit/s
        ff("-f", "lavfi", "-i", "nullsrc=size=1280x720:rate=30:duration=10,geq=random(1)*255:128:128", "-c:v", "libx264",
                "-b:v", "20M", "-pix_fmt", "yuv420p", media.resolve("grande.mp4").toString());
        Files.writeString(drive.resolve("DeLito/spec.json"), """
                {"out": "Semana 2", "leeme": "0_LEEME_semana_2.txt", "titulo_leeme": "EL DELITO - SEMANA 2",
                 "piezas": [
                  {"tipo": "carrusel", "nombre": "2_miercoles_carrusel", "dia": "Mie 4 nov 19:00",
                   "slides": [{"img": "material/page 46.png", "texto": "Un verso largo que tiene que partirse en varias líneas para caber", "titulo": "Con púa", "crop": [0.5, 0.3]},
                              {"img": "material/page 04.jpg", "texto": "Otro verso"}],
                   "cierre": {"img": "material/page 04.jpg", "lineas": ["El DeLito", "30 poemas que se leen… y se juegan", "Enlace en la bio"]},
                   "caption": "Texto del carrusel", "hashtags": "#poesia #granada"},
                  {"tipo": "historia", "nombre": "4_martes_historia", "dia": "Mar 3 nov 13:00",
                   "img": "material/page 46.png", "titulo": "Pregunta grande", "texto": "frase pequeña", "cta": "Enlace en la bio"},
                  {"tipo": "reel", "nombre": "1_lunes_reel", "dia": "Lun 2 nov 19:00", "img": "material/page 46.png",
                   "gancho": "Frase de 3 segundos", "versos": ["verso uno"],
                   "final": ["El DeLito", "un poema de Lito", "Música: Púa, de Litoques", "Enlace en la bio"],
                   "audio": "material/15 pua.m4a", "audio_inicio": 2, "caption": "Texto del reel"},
                  {"tipo": "clip", "nombre": "story_dia5", "video": "material/video.mp4", "inicio": 1, "duracion": 5,
                   "texto": "Escúchalo: enlace en la bio"},
                  {"tipo": "comprimir", "nombre": "Cuento_10MB", "video": "material/grande.mp4"}
                 ]}
                """);
    }

    @Test
    void generatesEveryPieceLikePromoPy() throws Exception {
        SpecReader.Loaded loaded = reader.load(drive.resolve("DeLito/spec.json").toString(), List.of(drive.resolve("DeLito").toString()));
        List<String> log = new ArrayList<>();
        PieceGenerator.Batch batch = generator.generate(loaded.spec(), loaded.files(), log::add, p -> {});
        Path out = drive.resolve("DeLito/Semana 2").toRealPath();
        assertThat(batch.outputDir()).isEqualTo(out);
        assertThat(log).hasSize(5);

        // Carrusel: 2 slides + cierre, 1080x1350
        for (int i = 1; i <= 3; i++) assertSize(out.resolve("2_miercoles_carrusel_" + i + ".jpg"), 1080, 1350);
        // Historia 1080x1920
        assertSize(out.resolve("4_martes_historia.jpg"), 1080, 1920);
        // Reel en vertical, con audio, menos de 9,5 MB, y su portada
        assertVideo(out.resolve("1_lunes_reel.mp4"), true);
        assertSize(out.resolve("1_lunes_reel_portada.jpg"), 1080, 1920);
        assertThat(ffmpeg.probeDuration(out.resolve("1_lunes_reel.mp4"))).isBetween(13.0, 16.0);
        // Clip vertical de 5 s
        assertVideo(out.resolve("story_dia5.mp4"), true);
        assertThat(ffmpeg.probeDuration(out.resolve("story_dia5.mp4"))).isBetween(4.5, 5.5);
        // Comprimir: el original (más de 9,5 MB) queda por debajo
        assertThat(Files.size(drive.resolve("DeLito/material/grande.mp4"))).isGreaterThan(9_500_000L);
        assertThat(Files.size(out.resolve("Cuento_10MB.mp4"))).isLessThanOrEqualTo(9_500_000L);
        // LEEME con los textos
        String readme = Files.readString(out.resolve("0_LEEME_semana_2.txt"));
        assertThat(readme).startsWith("EL DELITO - SEMANA 2").contains("Mie 4 nov 19:00 - CARRUSEL - 2_miercoles_carrusel")
                .contains("Texto del carrusel").contains("#poesia #granada")
                .contains("Archivos: 1_lunes_reel.mp4, 1_lunes_reel_portada.jpg");

        // Nunca sobrescribe: una segunda pasada crea copias numeradas y deja intactas las primeras
        long before = Files.getLastModifiedTime(out.resolve("4_martes_historia.jpg")).toMillis();
        PromoSpec again = new PromoSpec(loaded.spec().out(), loaded.spec().leeme(), loaded.spec().tituloLeeme(),
                List.of(loaded.spec().piezas().get(1)));
        generator.generate(again, loaded.files(), s -> {}, p -> {});
        assertThat(out.resolve("4_martes_historia_2.jpg")).exists();
        assertThat(out.resolve("0_LEEME_semana_2_2.txt")).exists();
        assertThat(Files.getLastModifiedTime(out.resolve("4_martes_historia.jpg")).toMillis()).isEqualTo(before);
        // No quedan temporales
        try (var files = Files.list(out)) {
            assertThat(files.map(f -> f.getFileName().toString())).noneMatch(n -> n.startsWith("."));
        }
    }

    @Test
    void onlyTouchesThePromotionFolders() throws Exception {
        String root = drive.resolve("DeLito").toString();
        Path outside = Files.createDirectories(drive.resolve("Otra"));
        image(outside.resolve("ajena.png"), 200, 200, Color.BLACK, Color.WHITE);
        Path spec = drive.resolve("DeLito/malo.json");

        Files.writeString(spec, "{\"piezas\":[{\"tipo\":\"historia\",\"nombre\":\"x\",\"img\":\"../Otra/ajena.png\",\"titulo\":\"t\"}]}");
        assertThatThrownBy(() -> reader.load(spec.toString(), List.of(root))).hasMessageContaining("Fuera de las carpetas");

        Files.writeString(spec, "{\"out\":\"../Otra\",\"piezas\":[{\"tipo\":\"historia\",\"nombre\":\"x\",\"img\":\"material/page 46.png\",\"titulo\":\"t\"}]}");
        var loaded = reader.load(spec.toString(), List.of(root));
        assertThatThrownBy(() -> generator.generate(loaded.spec(), loaded.files(), s -> {}, p -> {}))
                .hasMessageContaining("Fuera de las carpetas");

        Files.writeString(spec, "{\"piezas\":[{\"tipo\":\"historia\",\"nombre\":\"../x\",\"img\":\"material/page 46.png\",\"titulo\":\"t\"}]}");
        assertThatThrownBy(() -> reader.load(spec.toString(), List.of(root))).hasMessageContaining("Nombre de pieza no válido");

        Files.writeString(spec, "{\"piezas\":[{\"tipo\":\"video3d\",\"nombre\":\"x\"}]}");
        assertThatThrownBy(() -> reader.load(spec.toString(), List.of(root))).hasMessageContaining("tipo desconocido");

        Files.writeString(spec, "{\"piezas\":[{\"tipo\":\"historia\",\"nombre\":\"x\",\"img\":\"material/15 pua.m4a\",\"titulo\":\"t\"}]}");
        assertThatThrownBy(() -> reader.load(spec.toString(), List.of(root))).hasMessageContaining("Formato no admitido");

        Path link = drive.resolve("DeLito/atajo.png");
        try {
            Files.createSymbolicLink(link, outside.resolve("ajena.png"));
            Files.writeString(spec, "{\"piezas\":[{\"tipo\":\"historia\",\"nombre\":\"x\",\"img\":\"atajo.png\",\"titulo\":\"t\"}]}");
            assertThatThrownBy(() -> reader.load(spec.toString(), List.of(root))).hasMessageContaining("Fuera de las carpetas");
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            // Sin permiso para crear enlaces simbólicos (Windows sin modo desarrollador): no aplica
        } finally {
            Files.deleteIfExists(link);
        }

        assertThatThrownBy(() -> reader.load(outside.resolve("spec.json").toString(), List.of(root)))
                .hasMessageContaining("Fuera de las carpetas");
        assertThatThrownBy(() -> reader.load(spec.toString(), List.of(drive.resolve("NoExiste").toString())))
                .hasMessageContaining("ninguna carpeta accesible");
    }

    private static void assertSize(Path image, int w, int h) throws Exception {
        BufferedImage img = ImageIO.read(image.toFile());
        assertThat(img).as(image.toString()).isNotNull();
        assertThat(img.getWidth()).isEqualTo(w);
        assertThat(img.getHeight()).isEqualTo(h);
    }

    private static void assertVideo(Path video, boolean audio) throws Exception {
        assertThat(video).exists();
        assertThat(Files.size(video)).isLessThanOrEqualTo(9_500_000L);
        Path ffprobe = ffmpeg.ffprobe();
        String streams = new SafeProcess(ExternalCalls.unobserved()).run("ffprobe", "test", ffprobe,
                List.of("-v", "error", "-show_entries", "stream=codec_type,width,height", "-of", "csv=p=0", video.toString()),
                null, Duration.ofSeconds(30)).output();
        assertThat(streams).contains("video,1080,1920");
        if (audio) assertThat(streams).contains("audio");
    }

    private static void image(Path file, int w, int h, Color a, Color b) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, a, w, h, b));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.fillOval(w / 3, h / 3, w / 3, h / 3);
        g.dispose();
        ImageIO.write(img, file.toString().endsWith(".png") ? "png" : "jpg", file.toFile());
    }

    private static void ff(String... args) throws Exception {
        List<String> all = new ArrayList<>(List.of("-y", "-loglevel", "error"));
        all.addAll(List.of(args));
        ffmpeg.run("prueba", all, Duration.ofMinutes(2));
    }
}
