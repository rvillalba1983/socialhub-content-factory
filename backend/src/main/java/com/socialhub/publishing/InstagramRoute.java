package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import com.socialhub.publishing.domain.InstagramGateway;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicMediaHost;
import com.socialhub.publishing.domain.PublicMediaRepository;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.SecretStore;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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

    static final String KEY = "instagram-api";
    /** Margen para conseguir los enlaces públicos: Publer necesita 8 h ({@link PublerRoute#DEADLINE}) y 1 h para que Claude la programe. */
    static final Duration LINKS_CUTOFF = PublerRoute.DEADLINE.plusHours(1);
    static final int MAX_CAROUSEL = 20;

    private final SecretStore secrets;
    private final InstagramGateway instagram;
    private final InstagramAccountRepository accounts;
    private final PublicationFileRepository files;
    private final PromotionQueries promotions;
    private final PublicMediaHost host;
    private final PublicMediaRepository publicMedia;
    private final AiOrders orders;
    private final long pollMillis;
    private final int maxPolls;

    InstagramRoute(SecretStore secrets, InstagramGateway instagram, InstagramAccountRepository accounts,
                   PublicationFileRepository files, PromotionQueries promotions, PublicMediaHost host,
                   PublicMediaRepository publicMedia, @Lazy AiOrders orders,
                   @Value("${socialhub.instagram.poll-millis:10000}") long pollMillis,
                   @Value("${socialhub.instagram.max-polls:30}") int maxPolls) {
        this.secrets = secrets;
        this.instagram = instagram;
        this.accounts = accounts;
        this.files = files;
        this.promotions = promotions;
        this.host = host;
        this.publicMedia = publicMedia;
        this.orders = orders;
        this.pollMillis = pollMillis;
        this.maxPolls = maxPolls;
    }

    @Override public Id id() { return Id.OFFICIAL; }

    @Override public boolean configured() { return secrets.get(KEY).isPresent(); }

    @Override public boolean applies(Publication p) { return p.getPlatform() == Platform.INSTAGRAM; }

    @Override public boolean publishesAtTime() { return true; }

    @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(Publication.MIN_MARGIN); }

    @Override
    public Instant lastMoment(Publication p) {
        return needsPublicImages(p) ? p.scheduledAt().minus(LINKS_CUTOFF) : lastMoment(p.scheduledAt());
    }

    // ---------------------------------------------------------------- Programar: solo comprobar que se puede

    @Override
    public Outcome schedule(Publication p, PublishJob job, Instant now) {
        String key = secrets.get(KEY).orElse(null);
        if (key == null) return new Outcome.Failed("no configurada");
        String brand = promotions.brandOf(p.getPromotionId());
        var account = accounts.account(brand).orElse(null);
        if (account == null) return new Outcome.Failed("la marca no tiene su «Cuenta de Instagram (API oficial)»");
        Plan plan;
        try {
            plan = plan(p);
        } catch (Problem e) {
            return new Outcome.Failed(e.getMessage());
        }
        try {
            instagram.account(key, account.igUserId());
        } catch (InstagramException e) {
            return new Outcome.Failed(e.getMessage());
        }
        if (plan.images().isEmpty()) return new Outcome.Scheduled(null, null);

        // Imágenes: URL pública. Se dejan en la carpeta pública y Claude encuentra los enlaces
        String folder = promotions.brandOrdersFolder(brand).orElse(null);
        if (folder == null || !host.available(folder)) {
            return new Outcome.Failed("las imágenes necesitan la carpeta pública «publico» de Drive y no está disponible");
        }
        cleanup(p);
        List<PublicMediaRepository.Item> staged = new ArrayList<>();
        try {
            for (Path image : plan.images()) {
                String name = host.stage(folder, image).orElseThrow(() -> new Problem("no hay dónde dejar las imágenes públicas"));
                staged.add(new PublicMediaRepository.Item(p.getId(), image.toString(), name, null));
            }
        } catch (Problem | IllegalArgumentException e) {
            host.release(folder, staged.stream().map(PublicMediaRepository.Item::fileName).toList());
            return new Outcome.Failed(e.getMessage());
        }
        publicMedia.replace(p.getId(), staged);
        String task = orders.submitInternal(p.getPromotionId(), AiRequestType.PUBLIC_LINK,
                "Enlaces públicos: " + p.getTitle(),
                "Busca en Google Drive, dentro de la carpeta «publico», cada archivo de inputs.files y devuelve su enlace de descarga.",
                staged.stream().map(PublicMediaRepository.Item::fileName).toList());
        return new Outcome.Pending(task);
    }

    // ---------------------------------------------------------------- Publicar a la hora

    @Override
    public Outcome publish(Publication p, PublishJob job, Instant now) {
        String key = secrets.get(KEY).orElse(null);
        if (key == null) return new Outcome.Failed("la clave de la API de Instagram ya no está");
        String brand = promotions.brandOf(p.getPromotionId());
        var account = accounts.account(brand).orElse(null);
        if (account == null) return new Outcome.Failed("la marca ya no tiene su cuenta de Instagram (API oficial)");
        String ig = account.igUserId();
        String mediaId;
        try {
            Plan plan = plan(p);
            InstagramGateway.Quota quota = instagram.publishingLimit(key, ig);
            if (quota.exhausted()) {
                return new Outcome.Failed("límite de publicaciones por la API agotado (" + quota.used() + " de " + quota.total() + " en 24 h)");
            }
            Map<String, String> urls = publicUrls(p, plan);
            String container = buildContainer(key, ig, p, plan, urls);
            // A partir de aquí, un fallo de «media_publish» sin respuesta no permite saber si se publicó
            try {
                mediaId = instagram.publish(key, ig, container);
            } catch (InstagramException e) {
                if (e.kind() == InstagramException.Kind.UNCERTAIN) {
                    return new Outcome.Doubt("Meta no confirmó si la publicó: " + e.getMessage());
                }
                return new Outcome.Failed(e.getMessage());
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
        return new Outcome.Published(mediaId, url);
    }

    @Override
    public java.util.Optional<Outcome> check(Publication p, PublishJob job, Instant now) {
        String key = secrets.get(KEY).orElse(null);
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

    private String buildContainer(String key, String ig, Publication p, Plan plan, Map<String, String> urls) {
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
                return resumable(key, ig, params, "REELS", plan.items().getFirst().path());
            }
            default -> {
                Item item = plan.items().getFirst();
                Map<String, String> params = new LinkedHashMap<>();
                if (!caption.isEmpty()) params.put("caption", caption);
                return item.video() ? resumable(key, ig, params, "REELS", item.path()) : image(key, ig, params, urls.get(item.path().toString()));
            }
        }
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
        throw new Problem("Meta tardó demasiado en procesar el contenido");
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

    static final class Problem extends RuntimeException {
        Problem(String message) { super(message); }
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
