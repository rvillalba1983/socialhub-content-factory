package com.socialhub.pieces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.pieces.ApprovedTemplates;
import com.socialhub.pieces.adapter.out.svg.BatikSvgRasterizer;
import com.socialhub.pieces.adapter.out.svg.SvgTemplateRenderer;
import com.socialhub.pieces.domain.BrandLook;
import com.socialhub.pieces.domain.PieceBatch;
import com.socialhub.pieces.domain.PieceFiles;
import com.socialhub.pieces.domain.PromoSpec;
import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.socialhub.platform.process.SafeProcess;
import com.socialhub.tools.adapter.out.ffmpeg.FfmpegTool;
import com.socialhub.tools.application.FfmpegService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fase 13, bloque 5: reels y clips con escenas (FFmpeg real) y el audio del sonido de la publicación. */
class PieceScenesTest {

    @TempDir static Path data;
    static FfmpegTool ffmpeg;
    static Path track;
    static Path shortTrack;

    @TempDir Path drive;
    private PieceFiles files;
    private final List<String> log = new ArrayList<>();
    private List<ApprovedTemplates.Template> kit = List.of();
    private PieceGenerator generator;

    @BeforeAll
    static void tools() throws Exception {
        ffmpeg = SharedFfmpeg.get();
        track = data.resolve("Púa.mp3");
        ffmpeg.run("prueba", List.of("-y", "-loglevel", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=30",
                "-c:a", "libmp3lame", "-b:a", "128k", track.toString()), Duration.ofMinutes(2));
    }

    @BeforeAll
    static void shortTrack() throws Exception {
        shortTrack = data.resolve("Corto.mp3");
        ffmpeg.run("prueba", List.of("-y", "-loglevel", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=5",
                "-c:a", "libmp3lame", "-b:a", "128k", shortTrack.toString()), Duration.ofMinutes(2));
    }

    @BeforeEach
    void prepare() throws Exception {
        Files.createDirectories(drive.resolve("material"));
        BufferedImage image = new BufferedImage(1080, 1350, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(30, 60, 120));
        g.fillRect(0, 0, 1080, 1350);
        g.dispose();
        ImageIO.write(image, "png", drive.resolve("material/page 12.png").toFile());
        files = new PieceFiles(List.of(drive.toString()), drive);
        var rasterizer = new BatikSvgRasterizer();
        generator = new PieceGenerator(new FfmpegService(ffmpeg), rasterizer, new SvgTemplateRenderer(rasterizer),
                brand -> brand.equals("delito") ? kit : List.of(), name -> {
                    if (name.equalsIgnoreCase("Púa")) return track;
                    if (name.equalsIgnoreCase("Corto")) return shortTrack;
                    throw new IllegalArgumentException("No hay ningún MP3 del tema «" + name + "» en la carpeta de audio de Litoques");
                });
    }

    private static PromoSpec.Scene scene(String img, Map<String, String> fields, double seconds, String transition) {
        return new PromoSpec.Scene(null, fields, img, seconds, transition);
    }

    private static PromoSpec.Piece video(String tipo, String name, List<PromoSpec.Scene> scenes, PromoSpec.Sound sound) {
        return new PromoSpec.Piece(tipo, name, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, sound, scenes);
    }

    private PieceBatch run(PromoSpec.Piece piece) throws Exception {
        return generator.generate(new PromoSpec("salida", null, null, List.of(piece)), files, new BrandLook("#7A1F1F", null),
                log::add, p -> {}, new GenerationSetup("delito", id -> false));
    }

    private static List<PromoSpec.Scene> threeScenes() {
        return List.of(scene("material/page 12.png", Map.of("titulo", "Uno"), 2, null),
                scene(null, Map.of("titulo", "Dos", "texto", "Un verso"), 2, "fundido"),
                scene("material/page 12.png", null, 2, "fundido"));
    }

    private static String streams(Path video) throws Exception {
        return new SafeProcess(ExternalCalls.unobserved()).run("ffprobe", "test", ffmpeg.ffprobe(),
                List.of("-v", "error", "-show_entries", "stream=codec_type,width,height", "-of", "csv=p=0", video.toString()),
                null, Duration.ofSeconds(30)).output();
    }

    @Test
    void aReelMadeOfScenesIsOneVerticalVideoWithoutSoundAndWithACover() throws Exception {
        PieceBatch batch = run(video("reel", "mi_reel", threeScenes(), null));
        Path mp4 = batch.pieces().getFirst().files().getFirst();
        assertThat(mp4.getFileName().toString()).isEqualTo("mi_reel.mp4");
        assertThat(streams(mp4)).contains("video,1080,1920").doesNotContain("audio");
        // Tres escenas de 2 s, unidas sin recodificar: dura 6 s
        assertThat(ffmpeg.probeDuration(mp4)).isBetween(5.8, 6.3);
        BufferedImage cover = ImageIO.read(batch.pieces().getFirst().files().get(1).toFile());
        assertThat(cover.getWidth()).isEqualTo(1080);
        assertThat(cover.getHeight()).isEqualTo(1920);
        // No quedan restos de trabajo
        try (Stream<Path> out = Files.list(batch.outputDir())) {
            assertThat(out.map(f -> f.getFileName().toString())).noneMatch(n -> n.startsWith("."));
        }
    }

    @Test
    void fadeTransitionsGoThroughBlackAndCutsDoNot() throws Exception {
        PieceBatch batch = run(video("reel", "fundidos", List.of(scene("material/page 12.png", null, 2, null),
                scene("material/page 12.png", null, 2, "fundido")), null));
        Path mp4 = batch.pieces().getFirst().files().getFirst();
        // Fotograma justo en el empalme (t=2 s): casi negro por el fundido; en la mitad de la primera escena, no
        assertThat(brightness(mp4, 1.0)).isGreaterThan(20);
        assertThat(brightness(mp4, 1.99)).isLessThan(15);
        Path cut = run(video("reel", "cortes", List.of(scene("material/page 12.png", null, 2, null),
                scene("material/page 12.png", null, 2, "corte")), null)).pieces().getFirst().files().getFirst();
        assertThat(brightness(cut, 1.99)).isGreaterThan(20);
    }

    private static double brightness(Path video, double at) throws Exception {
        Path png = video.resolveSibling(video.getFileName() + "_" + at + ".png");
        ffmpeg.run("prueba", List.of("-y", "-loglevel", "error", "-ss", String.valueOf(at), "-i", video.toString(), "-frames:v", "1",
                png.toString()), Duration.ofSeconds(60));
        BufferedImage frame = ImageIO.read(png.toFile());
        Files.delete(png);
        return new Color(frame.getRGB(540, 960)).getBlue();
    }

    @Test
    void litoquesTrackIsMixedIntoTheSceneVideoKeepingItsLength() throws Exception {
        PromoSpec.Sound sound = new PromoSpec.Sound("litoques_track", "Púa");
        Path mp4 = run(video("reel", "con_tema", threeScenes(), sound)).pieces().getFirst().files().getFirst();
        assertThat(streams(mp4)).contains("video,1080,1920").contains("audio");
        // El tema dura 30 s y el reel 6: se recorta al vídeo
        assertThat(ffmpeg.probeDuration(mp4)).isBetween(5.8, 6.4);
        assertThat(log).anyMatch(l -> l.contains("se mezcla el tema «Púa»"));
    }

    @Test
    void networkAndNoneSoundsLeaveTheVideoWithoutATrack() throws Exception {
        for (String mode : List.of("network", "none")) {
            PromoSpec.Sound sound = new PromoSpec.Sound(mode, mode.equals("none") ? null : "Púa");
            Path mp4 = run(video("reel", "sin_pista_" + mode, threeScenes(), sound)).pieces().getFirst().files().getFirst();
            assertThat(streams(mp4)).doesNotContain("audio");
        }
    }

    @Test
    void aClipWithScenesHasNoCoverAndAMissingTrackStopsEverythingBeforeDrawing() throws Exception {
        PieceBatch clip = run(video("clip", "mi_clip", threeScenes(), null));
        assertThat(clip.pieces().getFirst().files()).hasSize(1);

        Path before = clip.outputDir();
        long filesBefore;
        try (Stream<Path> list = Files.list(before)) { filesBefore = list.count(); }
        assertThatThrownBy(() -> run(video("reel", "no_sale", threeScenes(), new PromoSpec.Sound("litoques_track", "Inexistente"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pieza «no_sale»").hasMessageContaining("No hay ningún MP3 del tema «Inexistente»");
        try (Stream<Path> list = Files.list(before)) { assertThat(list.count()).isEqualTo(filesBefore); }
    }

    @Test
    void aClipWithSoundDropsTheTrackOfTheOriginalVideo() throws Exception {
        Path source = drive.resolve("material/video.mp4");
        ffmpeg.run("prueba", List.of("-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30:duration=4",
                "-f", "lavfi", "-i", "sine=frequency=330:duration=4", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                "-shortest", source.toString()), Duration.ofMinutes(2));
        PromoSpec.Piece plain = new PromoSpec.Piece("clip", "clip_original", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "material/video.mp4", 0.0, 3.0, null);
        assertThat(streams(run(plain).pieces().getFirst().files().getFirst())).contains("audio");
        PromoSpec.Piece network = new PromoSpec.Piece("clip", "clip_red", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "material/video.mp4", 0.0, 3.0, null, null, null, null,
                new PromoSpec.Sound("network", "Púa"));
        assertThat(streams(run(network).pieces().getFirst().files().getFirst())).doesNotContain("audio");
        PromoSpec.Piece litoques = new PromoSpec.Piece("clip", "clip_tema", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "material/video.mp4", 0.0, 3.0, null, null, null, null,
                new PromoSpec.Sound("litoques_track", "Púa"));
        Path mixed = run(litoques).pieces().getFirst().files().getFirst();
        assertThat(streams(mixed)).contains("audio");
        assertThat(ffmpeg.probeDuration(mixed)).isBetween(2.8, 3.4);
    }

    @Test
    void aSceneUsesATemplateOfTheApprovedKit() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1080 1920\"><rect y=\"1500\" width=\"1080\" height=\"420\" fill=\"#000000\"/>"
                + "<rect id=\"caja-titulo\" x=\"60\" y=\"1560\" width=\"960\" height=\"200\" fill=\"none\"/>"
                + "<text id=\"campo-titulo\" font-family=\"Fira Sans\" font-size=\"60\" fill=\"#FFFFFF\">T</text></svg>";
        kit = List.of(new ApprovedTemplates.Template("historia-9x16", "historia", "Historia", svg,
                new ApprovedTemplates.Zone(0, 0, 1080, 1500), List.of("titulo"), List.of(), List.of(), "9:16", 1080, 1920));
        PromoSpec.Scene with = new PromoSpec.Scene("historia-9x16", Map.of("titulo", "Hola"), "material/page 12.png", 2, null);
        Path mp4 = run(video("reel", "con_plantilla", List.of(with, with), null)).pieces().getFirst().files().getFirst();
        assertThat(streams(mp4)).contains("video,1080,1920");
        // La banda negra de la plantilla está abajo y la ilustración arriba
        assertThat(brightness(mp4, 0.5)).isGreaterThan(20);

        PromoSpec.Scene wrong = new PromoSpec.Scene("no-existe", Map.of("titulo", "x"), null, 2, null);
        assertThatThrownBy(() -> run(video("reel", "mala", List.of(wrong), null)))
                .hasMessageContaining("Pieza «mala»").hasMessageContaining("no existe en el kit aprobado");
        PromoSpec.Scene missing = new PromoSpec.Scene("historia-9x16", Map.of(), null, 2, null);
        assertThatThrownBy(() -> run(video("reel", "sin_campos", List.of(missing), null)))
                .hasMessageContaining("le faltan campos obligatorios");
    }

    @Test
    void invalidScenesGiveSpanishErrors() {
        PromoSpec.Scene ok = scene(null, null, 2, null);
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", List.of(), null)), files)).hasMessageContaining("«escenas» está vacío");
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", java.util.Collections.nCopies(13, ok), null)), files))
                .hasMessageContaining("demasiadas escenas (13)");
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", List.of(scene(null, null, 0.5, null)), null)), files))
                .hasMessageContaining("la escena 1 tiene que durar entre 1 y 30 segundos");
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", List.of(scene(null, null, 31, null)), null)), files))
                .hasMessageContaining("la escena 1 tiene que durar");
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", java.util.Collections.nCopies(4, scene(null, null, 25, null)), null)), files))
                .hasMessageContaining("las escenas suman 100 segundos: el máximo es 90");
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", List.of(scene(null, null, 2, "barrido")), null)), files))
                .hasMessageContaining("la transición «barrido» de la escena 1 no vale");
        assertThatThrownBy(() -> generator.validate(spec(video("reel", "r", List.of(scene("material/no.png", null, 2, null)), null)), files))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.validate(spec(video("carrusel", "c", List.of(ok), null)), files))
                .hasMessageContaining("«escenas» solo se admite en un reel o un clip");
        PromoSpec.Piece both = new PromoSpec.Piece("reel", "r", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, "material/a.mp3", null, null, null, null, null, null, null, null, new PromoSpec.Sound("network", "Púa"),
                List.of(ok));
        assertThatThrownBy(() -> generator.validate(spec(both), files)).hasMessageContaining("lleva «audio» y también «sonido»");
        // Sin escenas, un reel sigue necesitando versos
        PromoSpec.Piece plain = new PromoSpec.Piece("reel", "r", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> generator.validate(spec(plain), files)).hasMessageContaining("no tiene versos");
    }

    private static PromoSpec spec(PromoSpec.Piece piece) {
        return new PromoSpec("salida", null, null, List.of(piece));
    }

    @Test
    void twoPiecesWithTheSameNameKeepTheirOwnSound() throws Exception {
        PromoSpec spec = new PromoSpec("salida", null, null, List.of(
                video("reel", "igual", threeScenes(), new PromoSpec.Sound("none", null)),
                video("reel", "igual", threeScenes(), new PromoSpec.Sound("litoques_track", "Púa"))));
        PieceBatch batch = generator.generate(spec, files, new BrandLook("#7A1F1F", null), log::add, p -> {},
                new GenerationSetup("delito", id -> false));
        assertThat(streams(batch.pieces().get(0).files().getFirst())).doesNotContain("audio");
        assertThat(streams(batch.pieces().get(1).files().getFirst())).contains("audio");
    }

    @Test
    void aClipWithoutScenesIgnoresAnAudioThatDoesNotExist() throws Exception {
        Path source = drive.resolve("material/video.mp4");
        ffmpeg.run("prueba", List.of("-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30:duration=4",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", source.toString()), Duration.ofMinutes(2));
        PromoSpec.Piece clip = new PromoSpec.Piece("clip", "clip_audio", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "material/no-existe.mp3", null, "material/video.mp4", 0.0, 3.0, null);
        assertThat(run(clip).pieces().getFirst().files()).hasSize(1);
    }

    @Test
    void theFadeOutOfAShortTrackFallsAtTheEndOfTheTrackNotOfTheReel() throws Exception {
        PromoSpec.Piece reel = new PromoSpec.Piece("reel", "reel_corto", null, null, null, null, null, null, null, null, null,
                null, null, null, List.of("Un verso", "Otro verso"), null, null, null, null, null, null, null, null, null, null,
                new PromoSpec.Sound("litoques_track", "Corto"), null);
        Path mp4 = run(reel).pieces().getFirst().files().getFirst();
        assertThat(ffmpeg.probeDuration(mp4)).isGreaterThan(8);
        // El tema dura 5 s: hacia el final del tema el sonido ya casi no se oye (y al principio, sí)
        assertThat(maxVolume(mp4, 4.6)).isLessThan(-25);
        assertThat(maxVolume(mp4, 1.5)).isGreaterThan(-20);
    }

    private static double maxVolume(Path video, double at) throws Exception {
        String out = ffmpeg.run("prueba", List.of("-hide_banner", "-ss", String.valueOf(at), "-t", "0.3", "-i", video.toString(),
                "-vn", "-af", "volumedetect", "-f", "null", "-"), Duration.ofSeconds(60)).output();
        var m = java.util.regex.Pattern.compile("max_volume: (-?[0-9.]+) dB").matcher(out);
        if (!m.find()) return -91;
        return Double.parseDouble(m.group(1));
    }

    @Test
    void aScenePathWithALineBreakIsRefusedInSpanish() {
        assertThatThrownBy(() -> new VideoAssembler(new FfmpegService(ffmpeg))
                .concat(List.of(Path.of("a\nb.mp4")), data.resolve("x.mp4")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("salto de línea");
    }
}
