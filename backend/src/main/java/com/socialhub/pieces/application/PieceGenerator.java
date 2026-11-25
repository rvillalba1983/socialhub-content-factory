package com.socialhub.pieces.application;

import static com.socialhub.pieces.application.Drawing.*;

import com.socialhub.pieces.ApprovedTemplates;
import com.socialhub.pieces.domain.BrandLook;
import com.socialhub.pieces.domain.PieceBatch;
import com.socialhub.pieces.domain.PieceFiles;
import com.socialhub.pieces.domain.PieceResult;
import com.socialhub.pieces.domain.PromoSpec;
import com.socialhub.pieces.domain.SvgRasterizer;
import com.socialhub.pieces.domain.TemplateRender;
import com.socialhub.pieces.domain.TemplateRenderer;
import com.socialhub.tools.Ffmpeg;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Genera las piezas de un spec.json igual que promo.py: carrusel 1080×1350 (1080×1920 en TikTok), historia 1080×1920, reel,
 * clip y compresión de vídeo a menos de 9,5 MB. Con un kit de marca aprobado y una {@code plantilla} en la pieza (cierre del
 * carrusel, historia o cita), la pieza se dibuja con la plantilla encima de la ilustración; sin kit o sin plantilla, todo
 * sale como siempre.
 */
@Component
class PieceGenerator {

    static final double MAX_MB = 9.5;
    private static final int W = 1080, H_STORY = 1920, H_POST = 1350, FPS = 30;
    private static final Duration VIDEO_TIMEOUT = Duration.ofMinutes(15);

    private static final int H_TALL_IMAGE = 1200;
    private static final Pattern FIELD = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final Set<String> SOUND_MODES = Set.of("none", "litoques_track", "network");
    static final int MAX_SCENES = 12;
    static final double MIN_SCENE_SECONDS = 1, MAX_SCENE_SECONDS = 30, MAX_TOTAL_SECONDS = 90;

    private final Ffmpeg ffmpeg;
    private final SvgRasterizer svg;
    private final TemplateRenderer templateRenderer;
    private final ApprovedTemplates approved;
    private final LitoquesTracks audio;
    private final VideoAssembler assembler;

    @Autowired
    PieceGenerator(Ffmpeg ffmpeg, SvgRasterizer svg, TemplateRenderer templateRenderer, ApprovedTemplates approved,
                   LitoquesTracks audio) {
        this.ffmpeg = ffmpeg;
        this.svg = svg;
        this.templateRenderer = templateRenderer;
        this.approved = approved;
        this.audio = audio;
        this.assembler = new VideoAssembler(ffmpeg);
    }

    /** Sin biblioteca de audio: un tema de Litoques pedido en el spec da error (para quien no necesita mezclar audio). */
    PieceGenerator(Ffmpeg ffmpeg, SvgRasterizer svg, TemplateRenderer templateRenderer, ApprovedTemplates approved) {
        this(ffmpeg, svg, templateRenderer, approved, name -> {
            throw new IllegalArgumentException("No hay «Carpeta de audio de Litoques» configurada para el tema «" + name + "»");
        });
    }

    /** Las plantillas del kit aprobado de la marca, la primera vez que se piden en un spec, y la red de cada publicación. */
    private record Kit(GenerationSetup setup, Supplier<List<ApprovedTemplates.Template>> templates) {
        static Kit of(GenerationSetup setup, ApprovedTemplates approved) {
            return new Kit(setup, memo(() -> setup.brandId() == null ? List.of() : approved.of(setup.brandId())));
        }

        private static <T> Supplier<T> memo(Supplier<T> source) {
            Object[] cache = new Object[1];
            return () -> {
                synchronized (cache) {
                    if (cache[0] == null) cache[0] = source.get();
                    @SuppressWarnings("unchecked") T value = (T) cache[0];
                    return value;
                }
            };
        }

        boolean vertical(PromoSpec.Piece p) {
            return notBlank(p.publicacion()) && setup.verticalNetwork().test(p.publicacion().strip());
        }
    }

    /** Lo que necesitan las piezas al dibujarse: los archivos de la promoción, la marca, el kit y dónde anotar lo que pasa. */
    private record Ctx(PieceFiles files, BrandLook look, Consumer<String> log, Kit kit) {}

    /**
     * La imagen base de una pieza o slide, por orden: su imagen propia, el {@code fondo_svg} de Claude, la portada de la
     * marca y, si no hay nada de eso, un fondo de SocialHub con el color de la marca. Un SVG que no vale o una portada que
     * no se puede leer no dan error: se pasa al siguiente.
     *
     * @param generated true si es el fondo de SocialHub (ya lleva el título, si se pidió)
     */
    private record Base(BufferedImage image, boolean generated) {}

    private Base base(Ctx ctx, @Nullable String img, @Nullable String fondoSvg, int svgW, int svgH, int genW, int genH,
                      String seed, @Nullable String drawTitle, double titleAt, String label) throws IOException {
        if (notBlank(img)) return new Base(read(ctx.files().input(img, PieceFiles.IMAGES, 60)), false);
        // Sin color de marca no se generan fondos: ni el SVG de Claude ni el de SocialHub
        if (ctx.look().hasColor() && notBlank(fondoSvg)) {
            var drawn = svg.rasterize(fondoSvg, svgW, svgH);
            if (drawn.isPresent()) {
                ctx.log().accept(label + ": sin imagen, se usa el fondo SVG de Claude");
                return new Base(drawn.get(), false);
            }
            ctx.log().accept(label + ": el fondo SVG no es válido o no se pudo pintar; se usa el siguiente fondo");
        }
        if (ctx.look().cover() != null) {
            try {
                BufferedImage cover = read(ctx.files().input(ctx.look().cover().toString(), PieceFiles.IMAGES, 60));
                ctx.log().accept(label + ": sin imagen, se usa la portada de la marca (" + ctx.look().cover().getFileName() + ")");
                return new Base(cover, false);
            } catch (IllegalArgumentException | IOException e) {
                ctx.log().accept(label + ": no se pudo usar la portada de la marca (" + e.getMessage() + ")");
            }
        }
        if (!ctx.look().hasColor()) throw noColor(label);
        ctx.log().accept(label + ": sin imagen, se usa un fondo con el color de la marca");
        return new Base(FallbackBackground.render(genW, genH, ctx.look().color(), seed, drawTitle, titleAt), true);
    }

    private static IllegalArgumentException noColor(String label) {
        return new IllegalArgumentException(label + ": la marca no tiene color, así que SocialHub no genera el fondo. "
                + "Elige el color de la marca en «Marcas y promociones», o pon una imagen o una portada");
    }

    /** El título de una pieza: el suyo o, si no lo tiene, su nombre sin guiones bajos. */
    private static String pieceTitle(PromoSpec.Piece p) {
        return notBlank(p.titulo()) ? p.titulo().strip() : p.nombre().replace('_', ' ').strip();
    }


    /** Comprueba el spec sin generar nada: tipos, nombres, rutas dentro de las carpetas, formatos y tamaños. */
    void validate(PromoSpec spec, PieceFiles files) {
        if (spec.piezas() == null || spec.piezas().isEmpty()) throw new IllegalArgumentException("El spec no tiene piezas");
        if (spec.piezas().size() > 60) throw new IllegalArgumentException("Demasiadas piezas en un spec (máximo 60)");
        files.checkOutputDir(spec.out() == null || spec.out().isBlank() ? "salida" : spec.out());
        for (PromoSpec.Piece p : spec.piezas()) {
            PieceFiles.safeName(p.nombre());
            checkPieceExtras(p);
            switch (p.tipo()) {
                case "carrusel" -> {
                    if (p.slides() == null || p.slides().isEmpty()) throw bad(p, "no tiene slides");
                    int n = 0;
                    for (PromoSpec.Slide s : p.slides()) {
                        n++;
                        if (notBlank(s.img())) files.input(s.img(), PieceFiles.IMAGES, 60);
                        else if (!notBlank(s.texto()) && !notBlank(s.titulo())) throw bad(p, "la slide " + n + " no tiene texto ni título");
                    }
                    if (p.cierre() != null && notBlank(p.cierre().img())) files.input(p.cierre().img(), PieceFiles.IMAGES, 60);
                }
                case "historia" -> {
                    if (notBlank(p.img())) files.input(p.img(), PieceFiles.IMAGES, 60);
                    // Con plantilla, el título va en sus campos; si no hay kit aprobado se usa el de «campos» (ver story)
                    if (!notBlank(p.plantilla())) required(p, p.titulo(), "titulo");
                }
                case "reel" -> {
                    if (notBlank(p.img())) files.input(p.img(), PieceFiles.IMAGES, 60);
                    if (p.escenas() == null && (p.versos() == null || p.versos().isEmpty())) {
                        throw bad(p, "no tiene versos (o «escenas» para montarlo con escenas)");
                    }
                    if (p.audio() != null) files.input(p.audio(), PieceFiles.AUDIO, 300);
                    checkSceneFiles(p, files);
                }
                case "clip" -> {
                    if (p.escenas() == null) files.input(required(p, p.video(), "video"), PieceFiles.VIDEO, 4096);
                    else if (notBlank(p.video())) files.input(p.video(), PieceFiles.VIDEO, 4096);
                    // Sin escenas, el clip conserva su propio audio y ignora «audio»: solo se valida si de verdad se usa
                    if (p.escenas() != null && p.audio() != null) files.input(p.audio(), PieceFiles.AUDIO, 300);
                    checkSceneFiles(p, files);
                }
                case "comprimir" -> files.input(required(p, p.video(), "video"), PieceFiles.VIDEO, 4096);
                case "cita" -> {
                    if (notBlank(p.img())) files.input(p.img(), PieceFiles.IMAGES, 60);
                    if (!notBlank(p.plantilla())) {
                        throw bad(p, "una cita necesita «plantilla» (una del kit de marca aprobado) y «campos» con el verso");
                    }
                }
                case "infografia", "meme" -> throw bad(p, "el tipo «" + p.tipo() + "» todavía no está activo en SocialHub "
                        + "(tipos activos: carrusel, historia, reel, clip, comprimir y cita)");
                default -> throw bad(p, "tipo desconocido «" + p.tipo() + "»");
            }
        }
    }

    /** Plantilla, campos, páginas y sonido: lo que se puede comprobar sin conocer la marca. */
    private static void checkPieceExtras(PromoSpec.Piece p) {
        boolean takesTemplate = p.tipo().equals("historia") || p.tipo().equals("cita");
        if (notBlank(p.plantilla()) && !takesTemplate) {
            throw bad(p, p.tipo().equals("carrusel")
                    ? "en un carrusel la plantilla va dentro de «cierre» («cierre.plantilla»), no en la pieza"
                    : "este tipo de pieza no admite plantilla (solo el cierre del carrusel, la historia y la cita)");
        }
        checkFields(p, "campos", p.campos());
        if (p.cierre() != null) checkFields(p, "cierre.campos", p.cierre().campos());
        checkPage(p, p.pagina());
        if (p.slides() != null) p.slides().forEach(s -> checkPage(p, s.pagina()));
        if (p.cierre() != null) checkPage(p, p.cierre().pagina());
        checkScenes(p);
        PromoSpec.Sound sound = p.sonido();
        if (sound != null && notBlank(p.audio())) {
            throw bad(p, "lleva «audio» y también «sonido»: usa solo uno (el sonido de la publicación sustituye al audio de la pieza)");
        }
        if (sound != null) {
            if (!SOUND_MODES.contains(sound.mode())) {
                throw bad(p, "sonido no válido «" + sound.mode() + "»: usa none (sin sonido), litoques_track (tema de Litoques) "
                        + "o network (elegir en la red)");
            }
            if (!sound.mode().equals("none") && !notBlank(sound.track())) {
                throw bad(p, "con el sonido «" + sound.mode() + "» hay que decir el nombre del tema («sonido.tema»)");
            }
            if (notBlank(sound.track()) && sound.track().strip().length() > 200) {
                throw bad(p, "el nombre del tema del sonido supera los 200 caracteres");
            }
        }
    }

    private static void checkSceneFiles(PromoSpec.Piece p, PieceFiles files) {
        if (p.escenas() == null) return;
        for (PromoSpec.Scene scene : p.escenas()) {
            if (notBlank(scene.img())) files.input(scene.img(), PieceFiles.IMAGES, 60);
        }
    }

    /** Las escenas de un reel o clip: cuántas, cuánto dura cada una y en total, y cómo entran. Sin tocar archivos ni marca. */
    private static void checkScenes(PromoSpec.Piece p) {
        List<PromoSpec.Scene> scenes = p.escenas();
        if (scenes == null) return;
        if (!p.tipo().equals("reel") && !p.tipo().equals("clip")) {
            throw bad(p, "«escenas» solo se admite en un reel o un clip (no en «" + p.tipo() + "»)");
        }
        if (scenes.isEmpty()) throw bad(p, "«escenas» está vacío: pon al menos una escena o quítalo");
        if (scenes.size() > MAX_SCENES) throw bad(p, "demasiadas escenas (" + scenes.size() + "): el máximo es " + MAX_SCENES);
        double total = 0;
        int n = 0;
        for (PromoSpec.Scene scene : scenes) {
            n++;
            if (!(scene.duracionS() >= MIN_SCENE_SECONDS && scene.duracionS() <= MAX_SCENE_SECONDS)) {
                throw bad(p, "la escena " + n + " tiene que durar entre " + (int) MIN_SCENE_SECONDS + " y " + (int) MAX_SCENE_SECONDS
                        + " segundos («duracion_s»)");
            }
            if (scene.transition() != null && !scene.transition().equals(PromoSpec.Scene.CUT) && !scene.fade()) {
                throw bad(p, "la transición «" + scene.transition() + "» de la escena " + n + " no vale: usa corte o fundido");
            }
            checkFields(p, "escenas[" + n + "].campos", scene.campos());
            total += scene.duracionS();
        }
        if (total > MAX_TOTAL_SECONDS) {
            throw bad(p, "las escenas suman " + num(total).replaceAll("\\.00$", "") + " segundos: el máximo es " + (int) MAX_TOTAL_SECONDS);
        }
    }

    private static void checkFields(PromoSpec.Piece p, String where, @Nullable Map<String, String> fields) {
        if (fields == null) return;
        if (fields.size() > 40) throw bad(p, "«" + where + "» tiene más de 40 campos");
        for (String name : fields.keySet()) {
            if (!FIELD.matcher(name).matches()) {
                throw bad(p, "el nombre de campo «" + name + "» (en «" + where + "») no vale: minúsculas, cifras y «_», "
                        + "empieza por letra, hasta 32 caracteres");
            }
        }
    }

    private static void checkPage(PromoSpec.Piece p, @Nullable Integer page) {
        if (page != null && (page < 1 || page > 9999)) throw bad(p, "la página «" + page + "» no vale: de 1 a 9999");
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    PieceBatch generate(PromoSpec spec, PieceFiles files, BrandLook look, Consumer<String> log, Consumer<Double> progress)
            throws IOException, InterruptedException {
        return generate(spec, files, look, log, progress, GenerationSetup.NONE);
    }

    PieceBatch generate(PromoSpec spec, PieceFiles files, BrandLook look, Consumer<String> log, Consumer<Double> progress,
                        GenerationSetup setup) throws IOException, InterruptedException {
        validate(spec, files);
        Ctx ctx = new Ctx(files, look, log, Kit.of(setup, approved));
        // Antes de dibujar nada: una plantilla que no existe o no sirve para su pieza para el lote entero
        checkTemplates(spec, ctx.kit(), log);
        // Y los temas de Litoques que hay que mezclar, también antes: si falta el MP3, no se genera nada a medias
        Map<Integer, Path> tracks = resolveTracks(spec, log);
        Path out = files.outputDir(spec.out() == null || spec.out().isBlank() ? "salida" : spec.out());
        List<PieceResult> results = new ArrayList<>();
        int i = 0;
        for (PromoSpec.Piece p : spec.piezas()) {
            checkInterrupted();
            Path track = tracks.get(i);
            List<PieceResult.Source> sources = new ArrayList<>();
            List<Path> made = switch (p.tipo()) {
                case "carrusel" -> carousel(p, ctx, out, sources);
                case "historia" -> story(p, ctx, out, sources);
                case "reel" -> p.escenas() != null ? scenes(p, ctx, out, sources, track)
                        : reel(p, ctx, out, sources, track);
                case "clip" -> p.escenas() != null ? scenes(p, ctx, out, sources, track)
                        : clip(p, files, out, track);
                case "comprimir" -> compress(p, files, out);
                case "cita" -> quote(p, ctx, out, sources);
                default -> throw bad(p, "tipo desconocido");
            };
            if (p.sonido() != null && blankToNull(p.publicacion()) == null) {
                log.accept(p.nombre() + ": el spec pide sonido pero la pieza no indica «publicacion»; no se guarda en ninguna");
            }
            results.add(new PieceResult(p.nombre(), p.tipo(), blankToNull(p.publicacion()), made, sources, p.sonido()));
            log.accept(p.tipo() + " · " + p.nombre() + ": " + describe(made));
            progress.accept(100.0 * ++i / spec.piezas().size());
        }
        Path readme = spec.leeme() == null || spec.leeme().isBlank() ? null : readme(spec, results, out);
        return new PieceBatch(out, results, readme);
    }

    /**
     * Los MP3 de las piezas de vídeo (reel y clip) con sonido {@code litoques_track}, por posición de la pieza (dos piezas
     * pueden llamarse igual). Si falta la
     * carpeta o el archivo, error en español antes de generar nada. En el resto de piezas el tema no se puede mezclar (no
     * hay vídeo): se avisa en el registro y la pieza sale sin audio.
     */
    private Map<Integer, Path> resolveTracks(PromoSpec spec, Consumer<String> log) {
        Map<Integer, Path> tracks = new java.util.HashMap<>();
        int index = -1;
        for (PromoSpec.Piece p : spec.piezas()) {
            index++;
            PromoSpec.Sound sound = p.sonido();
            if (sound == null || !sound.mode().equals("litoques_track")) continue;
            if (!p.tipo().equals("reel") && !p.tipo().equals("clip")) {
                log.accept(p.nombre() + ": el tema de Litoques solo se mezcla en reels y clips; esta pieza («" + p.tipo()
                        + "») sale sin audio y el sonido queda anotado en la publicación");
                continue;
            }
            try {
                Path track = audio.track(sound.track());
                tracks.put(index, track);
                log.accept(p.nombre() + ": se mezcla el tema «" + sound.track() + "» (" + track.getFileName() + ")");
            } catch (IllegalArgumentException e) {
                throw bad(p, e.getMessage());
            } catch (java.io.UncheckedIOException e) {
                throw bad(p, "No se puede leer la carpeta de audio de Litoques para buscar el tema «" + sound.track()
                        + "». ¿Está abierto Google Drive para ordenador?");
            }
        }
        return tracks;
    }

    // ---------------------------------------------------------------- carrusel

    private List<Path> carousel(PromoSpec.Piece p, Ctx ctx, Path out, List<PieceResult.Source> sources) throws IOException, InterruptedException {
        List<Path> made = new ArrayList<>();
        // En una red que pide 9:16 (TikTok) el carrusel sale en 1080×1920; en las demás, como siempre
        boolean tall = ctx.kit().vertical(p);
        int height = tall ? H_STORY : H_POST;
        int imageHeight = tall ? H_TALL_IMAGE : 900;
        int i = 0;
        for (PromoSpec.Slide s : p.slides()) {
            checkInterrupted();
            double[] crop = crop(s.crop(), 0.5, 0.4);
            boolean ownTitle = notBlank(s.titulo());
            String title = ownTitle ? s.titulo().strip() : pieceTitle(p);
            Base base = base(ctx, s.img(), notBlank(s.fondoSvg()) ? s.fondoSvg() : p.fondoSvg(), W, height, W, imageHeight,
                    title + (ownTitle ? "" : " " + (i + 1)), title, 0.5, p.nombre() + " (slide " + (i + 1) + ")");
            BufferedImage img = canvas(W, height, CREAM);
            Graphics2D g = graphics(img);
            g.drawImage(cover(base.image(), W, imageHeight, crop[0], crop[1]), 0, 0, null);
            int y = imageHeight + 60;
            // El fondo de SocialHub ya lleva el título de la slide: no se repite debajo
            if (ownTitle && !base.generated()) {
                centered(g, y, s.titulo(), bold(40), RED, W);
                y += 70;
            }
            if (notBlank(s.texto())) block(g, y, s.texto(), italic(46), INK, W);
            g.dispose();
            Path file = saveJpg(img, out, p.nombre() + "_" + ++i, 92);
            made.add(file);
            sources.add(source(file, s.pagina(), s.img()));
        }
        PromoSpec.Closing c = p.cierre();
        if (c != null) {
            Optional<ApprovedTemplates.Template> template = resolve(ctx.kit(), p, c.plantilla(), c.campos(), "cierre",
                    height, false, ctx.log());
            BufferedImage img;
            if (template.isPresent()) {
                ctx.log().accept(p.nombre() + " (cierre): con la plantilla «" + template.get().id() + "» del kit de marca");
                img = drawTemplate(ctx, p, template.get(), c.campos(), c.img(), p.fondoSvg(), W, height, pieceTitle(p) + " cierre",
                        0.5, 0.5, p.nombre() + " (cierre)");
            } else {
                int closeW = 900, closeH = tall ? 1100 : 800, closeY = tall ? 100 : 60;
                Base base = base(ctx, c.img(), p.fondoSvg(), W, height, closeW, closeH, pieceTitle(p) + " cierre", pieceTitle(p), 0.5,
                        p.nombre() + " (cierre)");
                img = canvas(W, height, CREAM);
                Graphics2D g = graphics(img);
                g.drawImage(cover(base.image(), closeW, closeH, 0.5, 0.5), 90, closeY, null);
                int y = tall ? 1300 : 900;
                // Pidió plantilla pero no hay kit aprobado y no trajo líneas: se usan el título y el texto de «campos»
                List<String> closingLines = !c.lineas().isEmpty() ? c.lineas()
                        : java.util.stream.Stream.of(field(c.campos(), "titulo"), field(c.campos(), "texto")).filter(PieceGenerator::notBlank).toList();
                for (int j = 0; j < closingLines.size(); j++) {
                    Font font = j == 0 ? bold(64) : italic(40);
                    y = block(g, y, closingLines.get(j), font, j == 0 ? INK : RED, W) + 10;
                }
                g.dispose();
            }
            Path file = saveJpg(img, out, p.nombre() + "_" + (made.size() + 1), 92);
            made.add(file);
            sources.add(source(file, c.pagina(), c.img()));
        }
        return made;
    }

    /** De dónde sale un archivo: su página del PDF y el nombre del archivo de imagen (sin la carpeta). */
    private static PieceResult.Source source(Path file, @Nullable Integer page, @Nullable String img) {
        String name = null;
        if (notBlank(img)) {
            String normalized = img.strip().replace('\\', '/');
            name = normalized.substring(normalized.lastIndexOf('/') + 1);
        }
        return new PieceResult.Source(file, page, name);
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

    private List<Path> story(PromoSpec.Piece p, Ctx ctx, Path out, List<PieceResult.Source> sources) throws IOException {
        double[] crop = crop(p.crop(), 0.5, 0.5);
        Optional<ApprovedTemplates.Template> template = resolve(ctx.kit(), p, p.plantilla(), p.campos(), "historia", H_STORY,
                false, ctx.log());
        if (template.isPresent()) {
            ctx.log().accept(p.nombre() + ": con la plantilla «" + template.get().id() + "» del kit de marca");
            BufferedImage card = drawTemplate(ctx, p, template.get(), p.campos(), p.img(), p.fondoSvg(), W, H_STORY, pieceTitle(p),
                    crop[0], crop[1], p.nombre());
            Path file = saveJpg(card, out, p.nombre(), 92);
            sources.add(source(file, p.pagina(), p.img()));
            return List.of(file);
        }
        // Si se pidió plantilla y no hay kit aprobado, la historia sale como siempre con los textos de «campos»
        String title = notBlank(p.titulo()) ? p.titulo() : field(p.campos(), "titulo");
        String text = notBlank(p.texto()) ? p.texto() : field(p.campos(), "texto");
        String cta = notBlank(p.cta()) ? p.cta() : field(p.campos(), "cta");
        if (!notBlank(title)) {
            throw bad(p, "falta «titulo»: sin un kit de marca aprobado la historia se dibuja sin plantilla y lo necesita");
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(title, bold(84), LIGHT));
        if (notBlank(text)) lines.add(new Line(text, italic(46), WARM));
        if (notBlank(cta)) lines.add(new Line(cta, bold(44), ACCENT));
        // Sin texto en el fondo propio de SocialHub: el título de la historia ya se dibuja encima
        Base base = base(ctx, p.img(), p.fondoSvg(), W, H_STORY, W, H_STORY, pieceTitle(p), null, 0.5, p.nombre());
        BufferedImage card = verticalCard(base.image(), lines, crop[0], crop[1], 160);
        Path file = saveJpg(card, out, p.nombre(), 92);
        sources.add(source(file, p.pagina(), p.img()));
        return List.of(file);
    }

    // ---------------------------------------------------------------- cita

    /** Una cita (verso sobre fondo): siempre con la plantilla del kit aprobado; el tamaño es el de la plantilla. */
    private List<Path> quote(PromoSpec.Piece p, Ctx ctx, Path out, List<PieceResult.Source> sources) throws IOException {
        boolean tall = ctx.kit().vertical(p);
        ApprovedTemplates.Template template = resolve(ctx.kit(), p, p.plantilla(), p.campos(), "cita", tall ? H_STORY : null, true,
                ctx.log()).orElseThrow();
        ctx.log().accept(p.nombre() + ": con la plantilla «" + template.id() + "» del kit de marca");
        int height = (int) Math.round((double) W * template.height() / template.width());
        double[] crop = crop(p.crop(), 0.5, 0.5);
        BufferedImage card = drawTemplate(ctx, p, template, p.campos(), p.img(), p.fondoSvg(), W, height, pieceTitle(p), crop[0],
                crop[1], p.nombre());
        Path file = saveJpg(card, out, p.nombre(), 92);
        sources.add(source(file, p.pagina(), p.img()));
        return List.of(file);
    }

    // ---------------------------------------------------------------- reel

    private record Segment(String text, double seconds, Font font) {}

    private List<Path> reel(PromoSpec.Piece p, Ctx ctx, Path out, List<PieceResult.Source> sources, @Nullable Path track)
            throws IOException, InterruptedException {
        PieceFiles files = ctx.files();
        Base base = base(ctx, p.img(), p.fondoSvg(), W, H_STORY, W, H_STORY, pieceTitle(p), pieceTitle(p), 0.36, p.nombre());
        BufferedImage src = base.image();
        // La tarjeta final escribe sus propias líneas: sobre el fondo de SocialHub, la versión sin título
        BufferedImage endSrc = base.generated()
                ? FallbackBackground.render(W, H_STORY, ctx.look().color(), pieceTitle(p), null, 0.5) : src;
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
                endImg = verticalCard(endSrc, lines, 0.5, 0.5, 185);
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
            if (notBlank(p.audio()) || track != null) {
                // El audio de la pieza (con «-shortest», como siempre) o el tema de Litoques del sonido (si es más corto
                // que el reel, el vídeo sigue en silencio)
                Path audioFile = track != null ? track : files.input(p.audio(), PieceFiles.AUDIO, 300);
                double start = track != null || p.audioInicio() == null ? 0 : p.audioInicio();
                // Si el audio es más corto que el reel, el fundido de salida va al final del audio (como en VideoAssembler)
                double available = ffmpeg.probeDuration(audioFile) - start;
                double effective = available > 0 ? Math.min(total, available) : total;
                double fadeIn = Math.min(1.5, effective / 3), fadeOut = Math.min(3.0, effective / 3);
                args.addAll(List.of("-ss", num(start), "-i", audioFile.toString(), "-t", num(total),
                        "-af", "afade=t=in:d=" + num(fadeIn) + ",afade=t=out:st=" + num(effective - fadeOut) + ":d=" + num(fadeOut),
                        "-c:a", "aac", "-b:a", "128k"));
                if (track == null) args.add("-shortest");
            }
            args.addAll(List.of("-c:v", "libx264", "-b:v", "950k", "-maxrate", "1200k", "-bufsize", "2000k",
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", video.toString()));
            ffmpeg.run("reel", args, VIDEO_TIMEOUT);
            Path cover = saveJpg(endImg, out, p.nombre() + "_portada", 92);
            Path finalVideo = fitSize(video);
            sources.add(source(finalVideo, p.pagina(), p.img()));
            sources.add(source(cover, p.pagina(), p.img()));
            return List.of(finalVideo, cover);
        } finally {
            deleteTree(frames);
        }
    }

    private static BufferedImage background(BufferedImage big, int mx, int my, double progress) {
        double q = Math.min(progress, 1.0);
        int x = (int) (mx * (0.15 + 0.7 * q)), y = (int) (my * (0.1 + 0.8 * q));
        return copy(big.getSubimage(x, y, W, H_STORY));
    }

    // ---------------------------------------------------------------- reel y clip con escenas

    /**
     * Reel o clip montado con escenas. Cada escena se dibuja como una imagen 1080×1920 (con su plantilla del kit aprobado o con
     * sus textos), se codifica en su propio archivo con los mismos parámetros y se une con las demás sin recodificar. El audio,
     * si lo hay (el tema de Litoques del sonido o el «audio» de la pieza), se pone después. El reel guarda además la primera
     * escena como portada.
     */
    private List<Path> scenes(PromoSpec.Piece p, Ctx ctx, Path out, List<PieceResult.Source> sources, @Nullable Path track)
            throws IOException, InterruptedException {
        List<PromoSpec.Scene> list = p.escenas();
        if (notBlank(p.video()) || notBlank(p.gancho()) || (p.versos() != null && !p.versos().isEmpty())
                || (p.finalLines() != null && !p.finalLines().isEmpty())) {
            ctx.log().accept(p.nombre() + ": con «escenas» se ignoran «video», «gancho», «versos» y «final»");
        }
        Path work = Files.createTempDirectory(out, ".escenas-");
        try {
            List<Path> segments = new ArrayList<>();
            BufferedImage first = null;
            for (int i = 0; i < list.size(); i++) {
                checkInterrupted();
                PromoSpec.Scene scene = list.get(i);
                BufferedImage still = sceneImage(p, ctx, scene, i + 1);
                if (first == null) first = still;
                Path png = work.resolve(String.format("escena_%02d.png", i));
                writePng(still, png);
                Path segment = work.resolve(String.format("escena_%02d.mp4", i));
                boolean fadeIn = i > 0 && scene.fade();
                boolean fadeOut = i + 1 < list.size() && list.get(i + 1).fade();
                assembler.encodeScene(png, scene.duracionS(), fadeIn, fadeOut, segment);
                segments.add(segment);
            }
            Path video = PieceFiles.fresh(out, p.nombre(), "mp4");
            assembler.concat(segments, video);
            if (track != null) {
                assembler.addAudio(video, track, 0);
            } else if (notBlank(p.audio())) {
                assembler.addAudio(video, ctx.files().input(p.audio(), PieceFiles.AUDIO, 300), p.audioInicio() == null ? 0 : p.audioInicio());
            }
            Path finalVideo = fitSize(video);
            sources.add(source(finalVideo, p.pagina(), list.getFirst().img()));
            if (!p.tipo().equals("reel")) return List.of(finalVideo);
            Path cover = saveJpg(first, out, p.nombre() + "_portada", 92);
            sources.add(source(cover, p.pagina(), list.getFirst().img()));
            return List.of(finalVideo, cover);
        } finally {
            deleteTree(work);
        }
    }

    /** La imagen de una escena: con su plantilla del kit aprobado; si no hay, con sus textos («titulo» y «texto») o solo la imagen. */
    private BufferedImage sceneImage(PromoSpec.Piece p, Ctx ctx, PromoSpec.Scene scene, int number) throws IOException {
        String label = p.nombre() + " · escena " + number;
        Optional<ApprovedTemplates.Template> template = resolveScene(ctx.kit(), p, scene, ctx.log());
        if (template.isPresent()) {
            return drawTemplate(ctx, p, template.get(), scene.campos(), scene.img(), null, W, H_STORY, label, 0.5, 0.5, label);
        }
        String title = field(scene.campos(), "titulo"), text = field(scene.campos(), "texto");
        List<Line> lines = new ArrayList<>();
        if (notBlank(title)) lines.add(new Line(title, bold(84), LIGHT));
        if (notBlank(text)) lines.add(new Line(text, italic(46), WARM));
        Base base = base(ctx, scene.img(), null, W, H_STORY, W, H_STORY, label, null, 0.5, label);
        if (lines.isEmpty()) return cover(base.image(), W, H_STORY, 0.5, 0.5);
        return verticalCard(base.image(), lines, 0.5, 0.5, 160);
    }

    /** La plantilla de una escena: es de «historia» o de «cita» (la que tenga en el kit) y tiene que ser 9:16. */
    private Optional<ApprovedTemplates.Template> resolveScene(Kit kit, PromoSpec.Piece p, PromoSpec.Scene scene, Consumer<String> log) {
        if (!notBlank(scene.plantilla())) return Optional.empty();
        String id = scene.plantilla().strip();
        String code = kit.templates().get().stream().filter(t -> t.id().equals(id)).map(ApprovedTemplates.Template::piece)
                .filter(c -> c.equals("cita")).findFirst().orElse("historia");
        return resolve(kit, p, id, scene.campos(), code, H_STORY, false, log);
    }

    // ---------------------------------------------------------------- plantillas del kit de marca

    /**
     * Comprueba, sin dibujar, las plantillas de las piezas del spec contra el kit aprobado de la marca: que existen, que son
     * de esa pieza, que llevan sus campos obligatorios y que su proporción es la que toca (9:16 en TikTok). Sin kit aprobado
     * no comprueba nada (las plantillas se ignoran), salvo en la cita, que no se puede dibujar sin plantilla.
     */
    void checkTemplates(PromoSpec spec, GenerationSetup setup, Consumer<String> log) {
        checkTemplates(spec, Kit.of(setup, approved), log);
    }

    private void checkTemplates(PromoSpec spec, Kit kit, Consumer<String> log) {
        for (PromoSpec.Piece p : spec.piezas()) {
            boolean tall = kit.vertical(p);
            switch (p.tipo()) {
                case "carrusel" -> {
                    if (p.cierre() != null) {
                        PromoSpec.Closing c = p.cierre();
                        if (resolve(kit, p, c.plantilla(), c.campos(), "cierre", tall ? H_STORY : H_POST, false, log).isEmpty()
                                && notBlank(c.plantilla()) && c.lineas().isEmpty()
                                && !notBlank(field(c.campos(), "titulo")) && !notBlank(field(c.campos(), "texto"))) {
                            throw bad(p, "el cierre pide plantilla, pero la marca no tiene kit aprobado y sin plantilla el cierre necesita "
                                    + "«lineas» (o «campos» con «titulo» o «texto»)");
                        }
                    }
                }
                case "historia" -> {
                    if (resolve(kit, p, p.plantilla(), p.campos(), "historia", H_STORY, false, log).isEmpty()
                            && notBlank(p.plantilla()) && !notBlank(p.titulo()) && !notBlank(field(p.campos(), "titulo"))) {
                        throw bad(p, "la historia pide plantilla, pero la marca no tiene kit aprobado y sin plantilla necesita «titulo» "
                                + "(en la pieza o en «campos»)");
                    }
                }
                case "cita" -> resolve(kit, p, p.plantilla(), p.campos(), "cita", tall ? H_STORY : null, true, log);
                case "reel", "clip" -> {
                    if (p.escenas() != null) p.escenas().forEach(scene -> resolveScene(kit, p, scene, log));
                }
                default -> { }
            }
        }
    }

    /**
     * La plantilla aprobada que pide la pieza. Sin {@code plantilla} o sin kit aprobado, vacío (la pieza sale como siempre; en
     * la cita, que no tiene otra forma de dibujarse, es un error). Con kit aprobado, una plantilla que no existe, que es de
     * otra pieza, a la que le faltan campos o que no tiene la proporción que toca, es un error en español.
     *
     * @param expectedHeight alto en píxeles que debe tener la pieza (el ancho es 1080); {@code null}: el que diga la plantilla
     */
    private Optional<ApprovedTemplates.Template> resolve(Kit kit, PromoSpec.Piece p, @Nullable String id,
                                                         @Nullable Map<String, String> fields, String pieceCode,
                                                         @Nullable Integer expectedHeight, boolean mandatory,
                                                         Consumer<String> log) {
        if (!notBlank(id)) return Optional.empty();
        String wanted = id.strip();
        List<ApprovedTemplates.Template> all = kit.templates().get();
        if (all.isEmpty()) {
            if (mandatory) {
                throw bad(p, "la marca no tiene un kit de marca aprobado, y una «" + pieceCode + "» no se puede dibujar sin su "
                        + "plantilla. Pide el kit en la ficha de la marca y apruébalo");
            }
            log.accept(p.nombre() + ": la marca no tiene un kit aprobado; se ignora la plantilla «" + wanted
                    + "» y la pieza sale como siempre");
            return Optional.empty();
        }
        ApprovedTemplates.Template template = all.stream().filter(t -> t.id().equals(wanted)).findFirst().orElseThrow(() -> {
            List<String> same = all.stream().filter(t -> t.piece().equals(pieceCode)).map(ApprovedTemplates.Template::id).toList();
            return bad(p, "la plantilla «" + wanted + "» no existe en el kit aprobado de la marca. "
                    + (same.isEmpty() ? "El kit no tiene plantillas de «" + pieceCode + "»"
                    : "Plantillas de «" + pieceCode + "»: " + String.join(", ", same)));
        });
        if (!template.piece().equals(pieceCode)) {
            throw bad(p, "la plantilla «" + wanted + "» es de la pieza «" + template.piece() + "» y aquí hace falta una de «"
                    + pieceCode + "»");
        }
        Map<String, String> texts = fields == null ? Map.of() : fields;
        List<String> missing = template.required().stream().filter(f -> !notBlank(texts.get(f))).toList();
        if (!missing.isEmpty()) {
            throw bad(p, "a la plantilla «" + wanted + "» le faltan campos obligatorios en «campos»: " + String.join(", ", missing));
        }
        if (expectedHeight != null && !sameRatio(template, W, expectedHeight)) {
            List<String> fit = all.stream().filter(t -> t.piece().equals(pieceCode) && sameRatio(t, W, expectedHeight))
                    .map(ApprovedTemplates.Template::id).toList();
            throw bad(p, "la plantilla «" + wanted + "» es " + template.ratio() + " y esta pieza sale en " + W + "×" + expectedHeight
                    + (expectedHeight == H_STORY ? " (9:16)" : " (4:5)")
                    + (fit.isEmpty() ? "; el kit no tiene otra plantilla de «" + pieceCode + "» con esa proporción"
                    : ". Usa una de esta proporción: " + String.join(", ", fit)));
        }
        return Optional.of(template);
    }

    private static boolean sameRatio(ApprovedTemplates.Template template, int width, int height) {
        double expected = (double) width / height;
        double real = (double) template.width() / template.height();
        return Math.abs(real - expected) / expected <= 0.01;
    }

    /**
     * Dibuja la pieza con su plantilla: la ilustración debajo, en la zona {@code image} de la plantilla, y la plantilla (dibujo y
     * textos) encima (decisión 53). Una plantilla sin zona de imagen (la cita) se dibuja sola.
     */
    private BufferedImage drawTemplate(Ctx ctx, PromoSpec.Piece p, ApprovedTemplates.Template template,
                                       @Nullable Map<String, String> fields, @Nullable String img, @Nullable String fondoSvg,
                                       int width, int height, String seed, double fx, double fy, String label) throws IOException {
        Map<String, String> texts = fields == null ? Map.of() : fields;
        ApprovedTemplates.Zone zone = template.image();
        if (zone == null) {
            TemplateRender render = templateRenderer.render(template.svg(), texts, width, height);
            return finish(p, template, render, label, ctx);
        }
        double scale = (double) width / template.width();
        int zx = (int) Math.round(zone.x() * scale), zy = (int) Math.round(zone.y() * scale);
        int zw = Math.max(1, (int) Math.round(zone.width() * scale)), zh = Math.max(1, (int) Math.round(zone.height() * scale));
        // Sin texto propio sobre el fondo de SocialHub: el título ya lo lleva la plantilla
        Base base = base(ctx, img, fondoSvg, zw, zh, zw, zh, seed, null, 0.5, label);
        TemplateRender over = templateRenderer.renderOver(template.svg(), texts, width, height);
        BufferedImage overlay = finish(p, template, over, label, ctx);
        BufferedImage canvas = canvas(width, height, CREAM);
        Graphics2D g = graphics(canvas);
        g.drawImage(cover(base.image(), zw, zh, fx, fy), zx, zy, null);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return canvas;
    }

    private static BufferedImage finish(PromoSpec.Piece p, ApprovedTemplates.Template template, TemplateRender render, String label,
                                        Ctx ctx) {
        if (!render.ok()) {
            throw bad(p, "no se pudo dibujar con la plantilla «" + template.id() + "»: " + String.join(" ", render.errors()));
        }
        for (String warning : render.warnings()) ctx.log().accept(label + " · plantilla «" + template.id() + "»: " + warning);
        return java.util.Objects.requireNonNull(render.image());
    }

    // ---------------------------------------------------------------- clip y comprimir

    private List<Path> clip(PromoSpec.Piece p, PieceFiles files, Path out, @Nullable Path track)
            throws IOException, InterruptedException {
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
            // Con sonido en la publicación, el clip sale sin la pista del vídeo original: sin pista («none» y «network») o con
            // el tema de Litoques mezclado después
            boolean ownAudio = p.sonido() == null;
            List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error",
                    "-ss", num(p.inicio() == null ? 0 : p.inicio()), "-t", num(p.duracion() == null ? 15 : p.duracion()),
                    "-i", video.toString(), "-i", ov.toString(), "-filter_complex", vf, "-map", "[o]"));
            if (ownAudio) args.addAll(List.of("-map", "0:a?", "-c:a", "aac", "-b:a", "128k"));
            else args.add("-an");
            args.addAll(List.of("-c:v", "libx264", "-b:v", "2500k", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                    result.toString()));
            ffmpeg.run("clip", args, VIDEO_TIMEOUT);
            if (track != null) assembler.addAudio(result, track, 0);
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

    private static @Nullable String field(@Nullable Map<String, String> fields, String name) {
        return fields == null ? null : fields.get(name);
    }

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
