package com.socialhub.pieces;

import static com.socialhub.pieces.Drawing.*;

import com.socialhub.tools.FfmpegTool;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Genera las piezas de un spec.json igual que promo.py: carrusel 1080×1350, historia 1080×1920, reel, clip y
 * compresión de vídeo a menos de 9,5 MB.
 */
@Component
class PieceGenerator {

    static final double MAX_MB = 9.5;
    private static final int W = 1080, H_STORY = 1920, H_POST = 1350, FPS = 30;
    private static final Duration VIDEO_TIMEOUT = Duration.ofMinutes(15);

    private final FfmpegTool ffmpeg;

    PieceGenerator(FfmpegTool ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /** @param publication publicación de SocialHub a la que van sus archivos, si el spec la indica */
    record PieceResult(String name, String type, @Nullable String publication, List<Path> files) {}

    record Batch(Path outputDir, List<PieceResult> pieces, @Nullable Path readme) {}

    /** Comprueba el spec sin generar nada: tipos, nombres, rutas dentro de las carpetas, formatos y tamaños. */
    void validate(PromoSpec spec, PieceFiles files) {
        if (spec.piezas() == null || spec.piezas().isEmpty()) throw new IllegalArgumentException("El spec no tiene piezas");
        if (spec.piezas().size() > 60) throw new IllegalArgumentException("Demasiadas piezas en un spec (máximo 60)");
        files.checkOutputDir(spec.out() == null || spec.out().isBlank() ? "salida" : spec.out());
        for (PromoSpec.Piece p : spec.piezas()) {
            PieceFiles.safeName(p.nombre());
            switch (p.tipo()) {
                case "carrusel" -> {
                    if (p.slides() == null || p.slides().isEmpty()) throw bad(p, "no tiene slides");
                    p.slides().forEach(s -> files.input(s.img(), PieceFiles.IMAGES, 60));
                    if (p.cierre() != null) files.input(p.cierre().img(), PieceFiles.IMAGES, 60);
                }
                case "historia" -> {
                    files.input(required(p, p.img(), "img"), PieceFiles.IMAGES, 60);
                    required(p, p.titulo(), "titulo");
                }
                case "reel" -> {
                    files.input(required(p, p.img(), "img"), PieceFiles.IMAGES, 60);
                    if (p.versos() == null || p.versos().isEmpty()) throw bad(p, "no tiene versos");
                    if (p.audio() != null) files.input(p.audio(), PieceFiles.AUDIO, 300);
                }
                case "clip", "comprimir" -> files.input(required(p, p.video(), "video"), PieceFiles.VIDEO, 4096);
                default -> throw bad(p, "tipo desconocido «" + p.tipo() + "»");
            }
        }
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    Batch generate(PromoSpec spec, PieceFiles files, Consumer<String> log, Consumer<Double> progress)
            throws IOException, InterruptedException {
        validate(spec, files);
        Path out = files.outputDir(spec.out() == null || spec.out().isBlank() ? "salida" : spec.out());
        List<PieceResult> results = new ArrayList<>();
        int i = 0;
        for (PromoSpec.Piece p : spec.piezas()) {
            checkInterrupted();
            List<Path> made = switch (p.tipo()) {
                case "carrusel" -> carousel(p, files, out);
                case "historia" -> story(p, files, out);
                case "reel" -> reel(p, files, out);
                case "clip" -> clip(p, files, out);
                case "comprimir" -> compress(p, files, out);
                default -> throw bad(p, "tipo desconocido");
            };
            results.add(new PieceResult(p.nombre(), p.tipo(), blankToNull(p.publicacion()), made));
            log.accept(p.tipo() + " · " + p.nombre() + ": " + describe(made));
            progress.accept(100.0 * ++i / spec.piezas().size());
        }
        Path readme = spec.leeme() == null || spec.leeme().isBlank() ? null : readme(spec, results, out);
        return new Batch(out, results, readme);
    }

    // ---------------------------------------------------------------- carrusel

    private List<Path> carousel(PromoSpec.Piece p, PieceFiles files, Path out) throws IOException, InterruptedException {
        List<Path> made = new ArrayList<>();
        int i = 0;
        for (PromoSpec.Slide s : p.slides()) {
            checkInterrupted();
            double[] crop = crop(s.crop(), 0.5, 0.4);
            BufferedImage img = canvas(W, H_POST, CREAM);
            Graphics2D g = graphics(img);
            g.drawImage(cover(read(files.input(s.img(), PieceFiles.IMAGES, 60)), W, 900, crop[0], crop[1]), 0, 0, null);
            int y = 960;
            if (s.titulo() != null && !s.titulo().isBlank()) {
                centered(g, y, s.titulo(), bold(40), RED, W);
                y += 70;
            }
            block(g, y, s.texto(), italic(46), INK, W);
            g.dispose();
            made.add(saveJpg(img, out, p.nombre() + "_" + ++i, 92));
        }
        PromoSpec.Closing c = p.cierre();
        if (c != null) {
            BufferedImage img = canvas(W, H_POST, CREAM);
            Graphics2D g = graphics(img);
            g.drawImage(cover(read(files.input(c.img(), PieceFiles.IMAGES, 60)), 900, 800, 0.5, 0.5), 90, 60, null);
            int y = 900;
            for (int j = 0; j < c.lineas().size(); j++) {
                Font font = j == 0 ? bold(64) : italic(40);
                y = block(g, y, c.lineas().get(j), font, j == 0 ? INK : RED, W) + 10;
            }
            g.dispose();
            made.add(saveJpg(img, out, p.nombre() + "_" + (made.size() + 1), 92));
        }
        return made;
    }

    // ---------------------------------------------------------------- historia

    private record Line(String text, Font font, Color color) {}

    private static BufferedImage verticalCard(BufferedImage source, List<Line> lines, double fx, double fy, int alpha) {
        BufferedImage s = cover(source, W, H_STORY, fx, fy);
        Graphics2D g = graphics(s);
        shade(g, 0, 0, W, H_STORY, alpha);
        int total = lines.stream().mapToInt(l -> l.font().getSize() + 40).sum();
        int y = (H_STORY - total) / 2 - 100;
        for (Line l : lines) y = block(g, y, l.text(), l.font(), l.color(), W) + 30;
        g.dispose();
        return s;
    }

    private List<Path> story(PromoSpec.Piece p, PieceFiles files, Path out) throws IOException {
        double[] crop = crop(p.crop(), 0.5, 0.5);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(p.titulo(), bold(84), LIGHT));
        if (notBlank(p.texto())) lines.add(new Line(p.texto(), italic(46), WARM));
        if (notBlank(p.cta())) lines.add(new Line(p.cta(), bold(44), ACCENT));
        BufferedImage card = verticalCard(read(files.input(p.img(), PieceFiles.IMAGES, 60)), lines, crop[0], crop[1], 160);
        return List.of(saveJpg(card, out, p.nombre(), 92));
    }

    // ---------------------------------------------------------------- reel

    private record Segment(String text, double seconds, Font font) {}

    private List<Path> reel(PromoSpec.Piece p, PieceFiles files, Path out) throws IOException, InterruptedException {
        BufferedImage src = read(files.input(p.img(), PieceFiles.IMAGES, 60));
        double sc = (H_STORY * 1.18) / src.getHeight();
        BufferedImage big = scale(src, (int) (src.getWidth() * sc), (int) (src.getHeight() * sc));
        int mx = Math.max(big.getWidth() - W, 0), my = Math.max(big.getHeight() - H_STORY, 0);
        List<Segment> segs = new ArrayList<>();
        if (notBlank(p.gancho())) segs.add(new Segment(p.gancho(), 4.0, bold(62)));
        for (String v : p.versos()) segs.add(new Segment(v, 4.0 + 0.045 * v.length(), italic(54)));
        double body = segs.stream().mapToDouble(Segment::seconds).sum();
        double end = 5.5;

        Path frames = Files.createTempDirectory(out, ".reel-");
        try {
            int k = 0;
            double t0 = 0;
            for (Segment seg : segs) {
                int n = (int) (seg.seconds() * FPS);
                for (int i = 0; i < n; i++) {
                    checkInterrupted();
                    BufferedImage fr = background(big, mx, my, (t0 + (double) i / FPS) / body);
                    Graphics2D g = graphics(fr);
                    List<String> ws = wrap(g, seg.text(), seg.font(), W - 160);
                    int lh = seg.font().getSize() + 22, bh = ws.size() * lh + 100, top = 1380 - bh / 2;
                    double a = Math.min(1.0, Math.min(i / 12.0, (n - i) / 10.0));
                    shade(g, 0, top, W, bh, (int) (200 * Math.max(a, 0.6)));
                    int yy = top + 50;
                    for (String line : ws) {
                        centered(g, yy, line, seg.font(), withAlpha(LIGHT, a), W);
                        yy += lh;
                    }
                    g.dispose();
                    writeJpg(fr, frames.resolve(String.format("%05d.jpg", k++)), 85);
                }
                t0 += seg.seconds();
            }
            List<String> fin = p.finalLines() == null ? List.of() : p.finalLines();
            BufferedImage endImg;
            if (!fin.isEmpty()) {
                List<Line> lines = new ArrayList<>();
                for (int j = 0; j < fin.size(); j++) {
                    Font font = j == 0 ? bold(96) : j < fin.size() - 1 ? italic(48) : bold(42);
                    Color color = j == 0 ? LIGHT : j < fin.size() - 1 ? WARM : ACCENT;
                    lines.add(new Line(fin.get(j), font, color));
                }
                endImg = verticalCard(src, lines, 0.5, 0.5, 185);
            } else {
                endImg = background(big, mx, my, 1.0);
            }
            Path endFrame = frames.resolve("end.jpg");
            writeJpg(endImg, endFrame, 85);
            for (int i = 0; i < (int) (end * FPS); i++) {
                Files.copy(endFrame, frames.resolve(String.format("%05d.jpg", k++)));
            }
            Files.delete(endFrame);
            double total = (double) k / FPS;

            Path video = PieceFiles.fresh(out, p.nombre(), "mp4");
            List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error", "-framerate", String.valueOf(FPS),
                    "-i", frames.resolve("%05d.jpg").toString()));
            if (notBlank(p.audio())) {
                Path audio = files.input(p.audio(), PieceFiles.AUDIO, 300);
                double start = p.audioInicio() == null ? 0 : p.audioInicio();
                args.addAll(List.of("-ss", num(start), "-i", audio.toString(), "-t", num(total),
                        "-af", "afade=t=in:d=1.5,afade=t=out:st=" + num(total - 3) + ":d=3",
                        "-c:a", "aac", "-b:a", "128k", "-shortest"));
            }
            args.addAll(List.of("-c:v", "libx264", "-b:v", "950k", "-maxrate", "1200k", "-bufsize", "2000k",
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", video.toString()));
            ffmpeg.run("reel", args, VIDEO_TIMEOUT);
            Path cover = saveJpg(endImg, out, p.nombre() + "_portada", 92);
            return List.of(fitSize(video), cover);
        } finally {
            deleteTree(frames);
        }
    }

    private static BufferedImage background(BufferedImage big, int mx, int my, double progress) {
        double q = Math.min(progress, 1.0);
        int x = (int) (mx * (0.15 + 0.7 * q)), y = (int) (my * (0.1 + 0.8 * q));
        return copy(big.getSubimage(x, y, W, H_STORY));
    }

    // ---------------------------------------------------------------- clip y comprimir

    private List<Path> clip(PromoSpec.Piece p, PieceFiles files, Path out) throws IOException, InterruptedException {
        Path video = files.input(p.video(), PieceFiles.VIDEO, 4096);
        BufferedImage overlay = new BufferedImage(W, H_STORY, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(overlay);
        Font font = bold(58);
        List<String> ws = wrap(g, notBlank(p.texto()) ? p.texto() : "Escúchalo: enlace en la bio", font, W - 160);
        int bh = ws.size() * (font.getSize() + 20) + 80;
        shade(g, 0, 160, W, bh, 190);
        int y = 200;
        for (String line : ws) {
            centered(g, y, line, font, LIGHT, W);
            y += font.getSize() + 20;
        }
        g.dispose();
        Path ov = Files.createTempFile(out, ".clip-", ".png");
        try {
            writePng(overlay, ov);
            Path result = PieceFiles.fresh(out, p.nombre(), "mp4");
            String vf = "[0:v]scale=" + W + ":" + H_STORY + ":force_original_aspect_ratio=increase,crop=" + W + ":" + H_STORY
                    + ",setsar=1[v];[v][1:v]overlay=0:0[o]";
            ffmpeg.run("clip", List.of("-y", "-loglevel", "error",
                    "-ss", num(p.inicio() == null ? 0 : p.inicio()), "-t", num(p.duracion() == null ? 15 : p.duracion()),
                    "-i", video.toString(), "-i", ov.toString(), "-filter_complex", vf, "-map", "[o]", "-map", "0:a?",
                    "-c:v", "libx264", "-b:v", "2500k", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart", result.toString()), VIDEO_TIMEOUT);
            return List.of(fitSize(result));
        } finally {
            Files.deleteIfExists(ov);
        }
    }

    private List<Path> compress(PromoSpec.Piece p, PieceFiles files, Path out) throws IOException, InterruptedException {
        Path video = files.input(p.video(), PieceFiles.VIDEO, 4096);
        Path result = PieceFiles.fresh(out, p.nombre(), "mp4");
        Files.copy(video, result);
        return List.of(fitSize(result));
    }

    /** Si pasa de 9,5 MB, se recomprime ajustando el bitrate a la duración (igual que promo.py). */
    private Path fitSize(Path video) throws IOException, InterruptedException {
        double mb = Files.size(video) / 1e6;
        if (mb <= MAX_MB) return video;
        double secs = Math.max(ffmpeg.probeDuration(video), 1);
        int vk = (int) (((MAX_MB * 8e6 * 0.92) / secs - 128e3) / 1000);
        if (vk < 100) throw new IllegalStateException("El vídeo es demasiado largo para quedar por debajo de " + MAX_MB + " MB");
        Path tmp = video.resolveSibling(video.getFileName() + ".tmp.mp4");
        try {
            ffmpeg.run("fit-size", List.of("-y", "-loglevel", "error", "-i", video.toString(), "-c:v", "libx264",
                    "-b:v", vk + "k", "-maxrate", (int) (vk * 1.25) + "k", "-bufsize", vk * 2 + "k",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", tmp.toString()),
                    VIDEO_TIMEOUT);
            Files.move(tmp, video, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return video;
    }

    // ---------------------------------------------------------------- LEEME

    private Path readme(PromoSpec spec, List<PieceResult> results, Path out) throws IOException {
        List<String> lines = new ArrayList<>(List.of(spec.tituloLeeme() == null ? "PROMOCION" : spec.tituloLeeme(), "=".repeat(60), ""));
        for (int i = 0; i < results.size(); i++) {
            PromoSpec.Piece p = spec.piezas().get(i);
            PieceResult r = results.get(i);
            lines.add((p.dia() == null ? "" : p.dia()) + " - " + p.tipo().toUpperCase(Locale.ROOT) + " - " + p.nombre());
            lines.add("Archivos: " + String.join(", ", r.files().stream().map(f -> f.getFileName().toString()).toList()));
            if (notBlank(p.caption())) { lines.add("Texto:"); lines.add(p.caption()); }
            if (notBlank(p.hashtags())) lines.add(p.hashtags());
            lines.add("");
        }
        String name = PieceFiles.safeName(spec.leeme());
        int dot = name.lastIndexOf('.');
        Path file = PieceFiles.fresh(out, dot < 0 ? name : name.substring(0, dot), dot < 0 ? "txt" : name.substring(dot + 1));
        Files.writeString(file, String.join("\n", lines), StandardCharsets.UTF_8);
        return file;
    }

    // ---------------------------------------------------------------- utilidades

    private static Path saveJpg(BufferedImage image, Path out, String name, int quality) throws IOException {
        Path file = PieceFiles.fresh(out, name, "jpg");
        writeJpg(image, file, quality);
        return file;
    }

    private static String describe(List<Path> files) {
        return String.join(", ", files.stream().map(f -> {
            try {
                return f.getFileName() + String.format(Locale.ROOT, " (%.2f MB)", Files.size(f) / 1e6);
            } catch (IOException e) {
                return f.getFileName().toString();
            }
        }).toList());
    }

    private static double[] crop(@Nullable List<Double> crop, double fx, double fy) {
        if (crop == null || crop.size() != 2) return new double[] {fx, fy};
        return new double[] {clamp(crop.get(0)), clamp(crop.get(1))};
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private static boolean notBlank(@Nullable String s) { return s != null && !s.isBlank(); }

    private static String required(PromoSpec.Piece p, @Nullable String value, String field) {
        if (!notBlank(value)) throw bad(p, "falta «" + field + "»");
        return value;
    }

    private static IllegalArgumentException bad(PromoSpec.Piece p, String message) {
        return new IllegalArgumentException("Pieza «" + p.nombre() + "»: " + message);
    }

    private static String num(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
