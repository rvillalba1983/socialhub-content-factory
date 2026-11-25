package com.socialhub.publishing.application;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishRouteId;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import com.socialhub.publishing.domain.InstagramGateway;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublicMediaHost;
import com.socialhub.publishing.domain.PublicMediaRepository;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationSound;
import com.socialhub.publishing.domain.PublishJob;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * API oficial de Instagram (Meta Graph API), primera vía para Instagram cuando está configurada (clave y cuenta de la
 * marca). Meta no programa: al programarla solo se comprueba que se puede, y SocialHub la publica él mismo a la hora
 * con contenedores (crear, esperar a {@code FINISHED}, {@code media_publish}). Imagen, carrusel (hasta 20), reel e
 * historia.
 * <ul>
 *   <li>Vídeos y reels: subida resumible desde el PC.</li>
 *   <li>Imágenes: Meta las descarga de una URL pública. SocialHub deja el JPEG en la carpeta pública de Drive y encarga a
 *       Claude (en la nube) su enlace; hasta que llega, la vía espera, y si no llega a tiempo se pasa a Publer.</li>
 *   <li>Si falla antes de publicar, la publicación sigue por la vía siguiente con el motivo anotado. Si hay duda de que
 *       Meta llegara a publicar, no se reintenta: queda para revisar.</li>
 * </ul>
 */
@Component
class InstagramRoute implements PublishRoute {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InstagramRoute.class);
    static final String KEY = "instagram-api";
    /**
     * El corte previo: hasta ahí la publicación aún puede ceder a Publer (8 h, {@link PublerRoute#DEADLINE}, y una pasada
     * de Claude con Chrome antes de ese límite). Los enlaces públicos tienen que haber llegado antes y, en ese momento, se
     * comprueba todo.
     */
    static Instant cutoff(Instant scheduledAt) { return PublerRoute.handoverBy(scheduledAt); }
    static final int MAX_CAROUSEL = 20;

    private final InstagramCredentials credentials;
    private final InstagramGateway instagram;
    private final InstagramAccountRepository accounts;
    private final PublicationFileRepository files;
    private final PromotionQueries promotions;
    private final PublicMediaHost host;
    private final PublicMediaRepository publicMedia;
    private final AiOrders orders;
    private final com.socialhub.settings.PublishSettings settings;
    private final long pollMillis;
    private final int maxPolls;

    InstagramRoute(InstagramCredentials credentials, InstagramGateway instagram, InstagramAccountRepository accounts,
                   PublicationFileRepository files, PromotionQueries promotions, PublicMediaHost host,
                   PublicMediaRepository publicMedia, AiOrders orders, com.socialhub.settings.PublishSettings settings,
                   @Value("${socialhub.instagram.poll-millis:10000}") long pollMillis,
                   @Value("${socialhub.instagram.max-polls:30}") int maxPolls) {
        this.credentials = credentials;
        this.instagram = instagram;
        this.accounts = accounts;
        this.files = files;
        this.promotions = promotions;
        this.host = host;
        this.publicMedia = publicMedia;
        this.orders = orders;
        this.settings = settings;
        this.pollMillis = pollMillis;
        this.maxPolls = maxPolls;
    }

    @Override public PublishRouteId id() { return PublishRouteId.OFFICIAL; }

    @Override public boolean configured() { return credentials.activeToken().isPresent(); }

    @Override public boolean applies(Publication p) { return p.getPlatform() == Platform.INSTAGRAM; }

    @Override public boolean publishesAtTime() { return true; }

    @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(settings.minMargin()); }

    @Override
    public Instant lastMoment(Publication p) {
        return needsPublicImages(p) ? cutoff(p) : lastMoment(p.scheduledAt());
    }

    @Override public Instant cutoff(Publication p) { return cutoff(p.scheduledAt()); }

    /** El corte previo: clave, cuenta, archivos y, para imágenes, los enlaces ya llegados y comprobados. */
    @Override
    public Outcome preflight(Publication p, PublishJob job, Instant now) {
        Outcome basic = checkBasics(p);
        if (!(basic instanceof Outcome.Scheduled)) return basic;
        try {
            Plan plan = plan(p);
            if (!plan.images().isEmpty()) publicUrls(p, plan);
        } catch (Problem e) {
            return new Outcome.Failed(e.getMessage());
        }
        return new Outcome.Scheduled(null, null);
    }

    // ---------------------------------------------------------------- Programar: solo comprobar que se puede

    @Override
    public Outcome schedule(Publication p, PublishJob job, Instant now) {
        Outcome basic = checkBasics(p);
        if (!(basic instanceof Outcome.Scheduled)) return basic;
        String brand = promotions.brandOf(p.getPromotionId());
        Plan plan = plan(p);
        if (plan.images().isEmpty()) return new Outcome.Scheduled(null, null);

        // Imágenes: URL pública. Se dejan en la carpeta pública y Claude encuentra los enlaces
        String folder = promotions.brandOrdersFolder(brand).orElse(null);
        if (folder == null || !host.available(folder)) {
            return new Outcome.Failed("las imágenes necesitan la carpeta pública «publico» de Drive y no está disponible");
        }
        // Las pasadas de Claude en la nube son cada 5 h: si ninguna llega a hacer el encargo antes del corte, ni se pide
        if (!com.socialhub.ai.domain.ClaudePasses.canMeet(false, cutoff(p.scheduledAt()),
                now.plus(com.socialhub.ai.domain.ClaudePickups.DISPATCH_MAX).plus(com.socialhub.ai.domain.ClaudePickups.SYNC_MARGIN))) {
            return new Outcome.Failed("ninguna pasada de Claude llega a dar los enlaces públicos antes del corte");
        }
        cleanup(p);
        // Si la transacción se deshace o encargar los enlaces falla, lo que se dejó en «publico» se borra
        List<String> names = new ArrayList<>();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) host.release(folder, List.copyOf(names));
                }
            });
        }
        List<PublicMediaRepository.Item> staged = new ArrayList<>();
        try {
            for (Path image : plan.images()) {
                String name = host.stage(folder, image).orElseThrow(() -> new Problem("no hay dónde dejar las imágenes públicas"));
                staged.add(new PublicMediaRepository.Item(p.getId(), image.toString(), name, null));
                names.add(name);
            }
        } catch (Problem | IllegalArgumentException e) {
            host.release(folder, staged.stream().map(PublicMediaRepository.Item::fileName).toList());
            return new Outcome.Failed(e.getMessage());
        }
        String task;
        try {
            publicMedia.replace(p.getId(), staged);
            task = orders.submitInternal(p.getPromotionId(), AiRequestType.PUBLIC_LINK,
                    "Enlaces públicos: " + p.getTitle(),
                    "Busca en Google Drive, dentro de la carpeta «publico», cada archivo de inputs.files y devuelve su id de Drive.",
                    List.copyOf(names));
        } catch (RuntimeException e) {
            host.release(folder, List.copyOf(names));
            throw e;
        }
        return new Outcome.Pending(task);
    }

    // ---------------------------------------------------------------- Publicar a la hora

    @Override
    public Outcome publish(Publication p, PublishJob job, Instant now) {
        String key = credentials.activeToken().orElse(null);
        if (key == null) return new Outcome.Failed("la clave de la API de Instagram ya no está");
        String brand = promotions.brandOf(p.getPromotionId());
        var account = accounts.account(brand).orElse(null);
        if (account == null) return new Outcome.Failed("la marca ya no tiene su cuenta de Instagram (API oficial)");
        String ig = account.igUserId();
        String mediaId;
        SoundAttempt soundOutcome;
        try {
            Plan plan = plan(p);
            InstagramGateway.Quota quota = instagram.publishingLimit(key, ig);
            if (quota.exhausted()) {
                return new Outcome.Failed("límite de publicaciones por la API agotado (" + quota.used() + " de " + quota.total() + " en 24 h)");
            }
            Map<String, String> urls = publicUrls(p, plan);
            SoundAttempt sound = new SoundAttempt();
            String container = buildContainer(key, ig, p, plan, urls, sound);
            soundOutcome = sound;
            // A partir de aquí, un fallo de «media_publish» sin respuesta no permite saber si se publicó
            try {
                mediaId = instagram.publish(key, ig, container);
            } catch (InstagramException e) {
                return afterPublishError(key, container, e);
            }
        } catch (Problem | InstagramException e) {
            return new Outcome.Failed(e.getMessage());
        }
        String url = null;
        try {
            url = instagram.permalink(key, mediaId);
        } catch (InstagramException e) {
            // Publicada, pero sin enlace todavía: se vuelve a mirar al comprobar
        }
        return new Outcome.Published(mediaId, url, soundOutcome.warning(p));
    }

    // ---------------------------------------------------------------- Sonido del reel (Instagram Audio API)

    /**
     * Qué pasó con el sonido de un reel con «elegir en la red: tema» (decisión 58). La Audio API solo busca y adjunta música
     * al crear el contenedor del reel; si no hay una coincidencia clara con el tema, o algo falla, el reel se publica igual,
     * sin audio, y queda el aviso «Falta elegir el sonido «X» en Instagram». Nunca bloquea la publicación.
     */
    static final class SoundAttempt {
        private boolean attached;

        void attached() { attached = true; }

        /**
         * El aviso de la publicación, o {@code null} si no hace falta (no pidió sonido de la red). Si el sonido se adjuntó, el
         * aviso es suave: con subida resumible Meta podría ignorar el audio, así que se pide comprobarlo en Instagram.
         */
        @Nullable String warning(Publication p) {
            if (p.getSound().mode() != PublicationSound.Mode.NETWORK) return null;
            if (attached) return SOUND_ATTACHED_FLAG;
            String track = p.getSound().track();
            return "Falta elegir el sonido" + (track == null || track.isBlank() ? "" : " «" + track + "»") + " en Instagram";
        }
    }

    /** Aviso suave cuando la API adjuntó el sonido (Meta lo acepta en el contenedor, pero no confirma que suene). */
    static final String SOUND_ATTACHED_FLAG = PublishingEngine.SOUND_ATTACHED_FLAG;

    /** Palabra que tiene que llevar el artista de la coincidencia: la música es del disco de Litoques. */
    static final String SOUND_ARTIST = "litoques";

    /**
     * Busca el tema en la biblioteca de Instagram y devuelve el {@code audio_configuration} si hay una coincidencia clara:
     * un único sonido cuyo título es el del tema y cuyo artista es Litoques. Cualquier otra cosa (sin resultados, varios,
     * otro artista, error de Meta) es vacío: el reel sale sin audio.
     */
    private java.util.Optional<String> audioConfiguration(String key, String ig, Publication p) {
        PublicationSound sound = p.getSound();
        if (sound.mode() != PublicationSound.Mode.NETWORK || sound.track() == null) return java.util.Optional.empty();
        String wanted = QualityChecker.normalize(sound.track()).replaceAll("[^\\p{L}\\p{N}]", "");
        try {
            var matches = instagram.searchMusic(key, ig, sound.track()).stream()
                    .filter(a -> QualityChecker.normalize(a.title()).replaceAll("[^\\p{L}\\p{N}]", "").equals(wanted))
                    .filter(a -> a.artist() != null && QualityChecker.normalize(a.artist()).contains(SOUND_ARTIST))
                    .map(InstagramGateway.Audio::id).distinct().toList();
            if (matches.size() != 1) {
                log.info("Audio API: {} coincidencias para «{}» en la publicación {}; sale sin audio", matches.size(), sound.track(), p.getId());
                return java.util.Optional.empty();
            }
            return java.util.Optional.of("{\"audio_id\":\"" + matches.getFirst().replace("\\", "").replace("\"", "") + "\"}");
        } catch (InstagramException e) {
            // Sin permiso, catálogo no disponible o error de Meta: nunca bloquea la publicación
            log.warn("Audio API no disponible para la publicación {}: {}", p.getId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Ante cualquier error de {@code media_publish} (también un 4xx), se mira cómo quedó el contenedor: si consta como
     * publicado, o no se puede saber, hay duda; solo si sigue en {@code FINISHED} se sabe que no se publicó.
     */
    private Outcome afterPublishError(String key, String container, InstagramException error) {
        InstagramGateway.Container state;
        try {
            state = instagram.container(key, container);
        } catch (InstagramException e) {
            return new Outcome.Doubt("Meta dio un error al publicar (" + error.getMessage()
                    + ") y no se pudo comprobar si la publicó: " + e.getMessage());
        }
        return switch (state.state()) {
            case FINISHED -> new Outcome.Failed(error.getMessage());
            case PUBLISHED -> new Outcome.Doubt("Meta consta que la publicó aunque dio un error (" + error.getMessage() + ")");
            default -> new Outcome.Doubt("Meta dio un error al publicar (" + error.getMessage() + ") y el contenedor quedó en "
                    + state.state());
        };
    }

    /** Clave, cuenta de la marca, archivos y que Meta reconoce la cuenta. */
    private Outcome checkBasics(Publication p) {
        String key = credentials.activeToken().orElse(null);
        if (key == null) return new Outcome.Failed("no configurada");
        String brand = promotions.brandOf(p.getPromotionId());
        var account = accounts.account(brand).orElse(null);
        if (account == null) return new Outcome.Failed("la marca no tiene su «Cuenta de Instagram (API oficial)»");
        try {
            plan(p);
        } catch (Problem e) {
            return new Outcome.Failed(e.getMessage());
        }
        try {
            instagram.account(key, account.igUserId());
        } catch (InstagramException e) {
            return new Outcome.Failed(e.getMessage());
        }
        return new Outcome.Scheduled(null, null);
    }

    @Override
    public java.util.Optional<Outcome> check(Publication p, PublishJob job, Instant now) {
        String key = credentials.activeToken().orElse(null);
        if (key == null) return java.util.Optional.of(new Outcome.NotYet("la clave de la API de Instagram ya no está"));
        if (job.getExternalId() == null) return java.util.Optional.of(new Outcome.Failed("sin identificador de Meta"));
        try {
            String url = instagram.permalink(key, job.getExternalId());
            return java.util.Optional.of(url == null ? new Outcome.NotYet("Meta aún no da el enlace")
                    : new Outcome.Published(job.getExternalId(), url));
        } catch (InstagramException e) {
            return java.util.Optional.of(new Outcome.NotYet(e.getMessage()));
        }
    }

    @Override
    public boolean unschedule(Publication p, PublishJob job) {
        cleanup(p);
        return true;
    }

    @Override
    public void cleanup(Publication p) {
        var items = publicMedia.items(p.getId());
        if (items.isEmpty()) return;
        promotions.brandOrdersFolder(promotions.brandOf(p.getPromotionId()))
                .ifPresent(folder -> host.release(folder, items.stream().map(PublicMediaRepository.Item::fileName).toList()));
        publicMedia.delete(p.getId());
    }

    // ---------------------------------------------------------------- Contenedores

    private String buildContainer(String key, String ig, Publication p, Plan plan, Map<String, String> urls, SoundAttempt sound) {
        String caption = caption(p);
        switch (plan.kind()) {
            case CAROUSEL -> {
                List<String> children = new ArrayList<>();
                for (Item item : plan.items()) {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("is_carousel_item", "true");
                    String id = item.video() ? resumable(key, ig, params, "VIDEO", item.path()) : image(key, ig, params, urls.get(item.path().toString()));
                    children.add(id);
                }
                Map<String, String> params = new LinkedHashMap<>();
                params.put("media_type", "CAROUSEL");
                params.put("children", String.join(",", children));
                if (!caption.isEmpty()) params.put("caption", caption);
                return finished(key, instagram.createContainer(key, ig, params));
            }
            case STORY -> {
                Item item = plan.items().getFirst();
                Map<String, String> params = new LinkedHashMap<>();
                if (item.video()) return resumable(key, ig, params, "STORIES", item.path());
                params.put("media_type", "STORIES");
                return image(key, ig, params, urls.get(item.path().toString()));
            }
            case REEL -> {
                Map<String, String> params = new LinkedHashMap<>();
                if (!caption.isEmpty()) params.put("caption", caption);
                return reelWithSound(key, ig, p, params, plan.items().getFirst().path(), sound);
            }
            default -> {
                Item item = plan.items().getFirst();
                Map<String, String> params = new LinkedHashMap<>();
                if (!caption.isEmpty()) params.put("caption", caption);
                return item.video() ? reelWithSound(key, ig, p, params, item.path(), sound) : image(key, ig, params, urls.get(item.path().toString()));
            }
        }
    }

    /**
     * Reel con el sonido de la red, si se encuentra en la Audio API. Si Meta no acepta el contenedor con el audio (o no lo
     * procesa porque el contenedor acaba en {@code ERROR} o {@code EXPIRED}), se repite sin él: el reel se publica igual, sin
     * audio. Ni un fallo sin respuesta clara ({@code UNCERTAIN}) ni el tiempo agotado del sondeo se repiten aquí: el contenedor
     * podría seguir procesándose y un segundo intento duplicaría el trabajo.
     */
    private String reelWithSound(String key, String ig, Publication p, Map<String, String> params, Path file, SoundAttempt sound) {
        var config = audioConfiguration(key, ig, p);
        if (config.isPresent()) {
            Map<String, String> withAudio = new LinkedHashMap<>(params);
            withAudio.put("audio_configuration", config.get());
            try {
                String container = resumable(key, ig, withAudio, "REELS", file);
                sound.attached();
                return container;
            } catch (PollTimeout e) {
                throw e;
            } catch (Problem e) {
                log.warn("Meta no procesó el reel con audio en la publicación {} ({}); se publica sin audio", p.getId(), e.getMessage());
            } catch (InstagramException e) {
                if (e.kind() == InstagramException.Kind.UNCERTAIN || e.kind() == InstagramException.Kind.INVALID_KEY) throw e;
                log.warn("Meta rechazó el reel con audio en la publicación {} ({}); se publica sin audio", p.getId(), e.getMessage());
            }
        }
        return resumable(key, ig, params, "REELS", file);
    }

    private String image(String key, String ig, Map<String, String> params, @Nullable String url) {
        if (url == null) throw new Problem("falta el enlace público de una imagen");
        params.put("image_url", url);
        return finished(key, instagram.createContainer(key, ig, params));
    }

    /** Vídeo por subida resumible: contenedor con {@code upload_type=resumable}, subida y espera. */
    private String resumable(String key, String ig, Map<String, String> params, String mediaType, Path file) {
        params.put("media_type", mediaType);
        params.put("upload_type", "resumable");
        String container = instagram.createContainer(key, ig, params);
        instagram.upload(key, container, file);
        return finished(key, container);
    }

    /** Espera a {@code FINISHED} (con reintentos y tope). {@code ERROR} o {@code EXPIRED}: no se publica. */
    private String finished(String key, String container) {
        for (int i = 0; i < maxPolls; i++) {
            InstagramGateway.Container state = instagram.container(key, container);
            switch (state.state()) {
                case FINISHED, PUBLISHED -> { return container; }
                case ERROR, EXPIRED -> throw new Problem("Meta no pudo procesar el contenido (" + state.state()
                        + (state.detail() == null ? "" : ": " + state.detail()) + ")");
                case IN_PROGRESS -> sleep();
            }
        }
        throw new PollTimeout("Meta tardó demasiado en procesar el contenido");
    }

    private void sleep() {
        try {
            Thread.sleep(pollMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Problem("interrumpida mientras esperaba a Meta");
        }
    }

    /** Ruta de cada imagen → su URL pública, ya comprobada. */
    private Map<String, String> publicUrls(Publication p, Plan plan) {
        Map<String, String> urls = new LinkedHashMap<>();
        if (plan.images().isEmpty()) return urls;
        var items = publicMedia.items(p.getId());
        for (Path image : plan.images()) {
            var item = items.stream().filter(i -> i.sourcePath().equals(image.toString())).findFirst().orElse(null);
            if (item == null || item.url() == null) throw new Problem("falta el enlace público de " + image.getFileName());
            URI uri;
            try {
                uri = URI.create(item.url());
            } catch (IllegalArgumentException e) {
                throw new Problem("enlace público no válido de " + image.getFileName());
            }
            host.problem(uri).ifPresent(why -> { throw new Problem(image.getFileName() + ": " + why); });
            urls.put(image.toString(), item.url());
        }
        return urls;
    }

    // ---------------------------------------------------------------- Qué se publica

    record Item(Path path, boolean video) {}

    record Plan(QualityChecker.Kind kind, List<Item> items) {
        List<Path> images() { return items.stream().filter(i -> !i.video()).map(Item::path).toList(); }
    }

    static class Problem extends RuntimeException {
        Problem(String message) { super(message); }
    }

    /** Se agotó la espera al contenedor: no se sabe si Meta lo acabará procesando, así que no se repite sin audio. */
    static final class PollTimeout extends Problem {
        PollTimeout(String message) { super(message); }
    }

    private boolean needsPublicImages(Publication p) {
        if (p.getPlatform() != Platform.INSTAGRAM) return false;
        QualityChecker.Kind kind = QualityChecker.kind(p.getType());
        // La portada de un reel no se manda por la API (haría falta otra URL pública)
        if (kind == QualityChecker.Kind.REEL || kind == QualityChecker.Kind.VIDEO) return false;
        return files.files(p.getId()).stream().anyMatch(f -> imageName(f.path()));
    }

    private Plan plan(Publication p) {
        QualityChecker.Kind kind = QualityChecker.kind(p.getType());
        List<Item> items = new ArrayList<>();
        for (PublicationFile f : files.files(p.getId())) {
            Path path;
            try {
                path = Path.of(f.path());
            } catch (InvalidPathException e) {
                throw new Problem("ruta no válida: " + f.path());
            }
            if (!Files.isRegularFile(path)) throw new Problem("no se encuentra " + f.path() + " (¿Drive sin sincronizar?)");
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (imageName(name)) items.add(new Item(path, false));
            else if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".m4v")) items.add(new Item(path, true));
            else throw new Problem("formato que la API de Instagram no admite: " + path.getFileName());
        }
        if (kind == QualityChecker.Kind.REEL || kind == QualityChecker.Kind.VIDEO) {
            // La portada (imagen) no se manda por la API: solo el vídeo
            items = new ArrayList<>(items.stream().filter(Item::video).toList());
        }
        if (items.isEmpty()) throw new Problem("no tiene archivos");
        switch (kind) {
            case CAROUSEL -> {
                if (items.size() < 2 || items.size() > MAX_CAROUSEL) {
                    throw new Problem("un carrusel lleva de 2 a " + MAX_CAROUSEL + " archivos y tiene " + items.size());
                }
            }
            case REEL, VIDEO -> {
                if (items.size() != 1 || !items.getFirst().video()) throw new Problem("un reel lleva un solo vídeo");
            }
            case STORY -> {
                if (items.size() != 1) throw new Problem("una historia lleva un solo archivo");
            }
            default -> {
                if (items.size() != 1) throw new Problem("una publicación sin carrusel lleva un solo archivo");
            }
        }
        QualityChecker.Kind effective = kind == QualityChecker.Kind.VIDEO ? QualityChecker.Kind.REEL : kind;
        return new Plan(effective, items);
    }

    private static boolean imageName(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    /** Texto y hashtags. Las historias no llevan texto. */
    private static String caption(Publication p) {
        if (QualityChecker.kind(p.getType()) == QualityChecker.Kind.STORY) return "";
        String caption = p.getCaption() == null ? "" : p.getCaption().strip();
        String tags = p.getHashtags() == null ? "" : p.getHashtags().strip();
        return tags.isEmpty() ? caption : caption.isEmpty() ? tags : caption + "\n\n" + tags;
    }
}
