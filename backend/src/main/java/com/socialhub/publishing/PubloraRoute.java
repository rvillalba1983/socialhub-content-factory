package com.socialhub.publishing;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PubloraAccountRepository;
import com.socialhub.publishing.domain.PubloraGateway;
import com.socialhub.publishing.domain.PublishJob;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publora, segunda vía: API REST con la clave de Raúl (plan gratuito: pocas publicaciones al mes, por eso va de
 * reserva). Sube los archivos, la programa a su hora y, pasada la hora, consulta si está publicada.
 */
@Component
@Order(2)
class PubloraRoute implements PublishRoute {

    static final String KEY = "publora";
    static final long MAX_IMAGE = 25L * 1024 * 1024;
    static final long MAX_VIDEO = 150L * 1024 * 1024;

    private final PubloraGateway publora;
    private final com.socialhub.publishing.domain.SecretStore secrets;
    private final PubloraAccountRepository accounts;
    private final PublicationFileRepository files;
    private final PromotionQueries promotions;

    PubloraRoute(PubloraGateway publora, com.socialhub.publishing.domain.SecretStore secrets,
                 PubloraAccountRepository accounts, PublicationFileRepository files, PromotionQueries promotions) {
        this.publora = publora;
        this.secrets = secrets;
        this.accounts = accounts;
        this.files = files;
        this.promotions = promotions;
    }

    @Override public Id id() { return Id.PUBLORA; }

    @Override public boolean configured() { return secrets.get(KEY).isPresent(); }

    @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(Publication.MIN_MARGIN); }

    @Override
    public Outcome schedule(Publication p, PublishJob job, Instant now) {
        String key = secrets.get(KEY).orElse(null);
        if (key == null) return new Outcome.Failed("no configurada");
        String brand = promotions.brandOf(p.getPromotionId());
        var account = accounts.account(brand, p.getPlatform()).orElse(null);
        if (account == null) {
            return new Outcome.Failed("la marca no tiene elegida su cuenta de " + label(p.getPlatform()) + " en Publora");
        }
        QualityChecker.Kind kind = QualityChecker.kind(p.getType());
        List<PubloraGateway.Media> media = new ArrayList<>();
        for (var f : files.files(p.getId())) {
            Path path;
            try {
                path = Path.of(f.path());
            } catch (InvalidPathException e) {
                return new Outcome.Failed("ruta no válida: " + f.path());
            }
            if (!Files.isRegularFile(path)) return new Outcome.Failed("no se encuentra " + f.path() + " (¿Drive sin sincronizar?)");
            String type = contentType(path);
            if (type == null) return new Outcome.Failed("formato que Publora no admite: " + path.getFileName());
            boolean video = type.startsWith("video/");
            try {
                long size = Files.size(path);
                if (size > (video ? MAX_VIDEO : MAX_IMAGE)) {
                    return new Outcome.Failed(path.getFileName() + " pasa del máximo de Publora (" + (video ? 150 : 25) + " MB)");
                }
            } catch (IOException e) {
                return new Outcome.Failed("no se puede leer " + f.path());
            }
            media.add(new PubloraGateway.Media(path, type, video));
        }
        if (media.isEmpty()) return new Outcome.Failed("no tiene archivos");
        if (kind == QualityChecker.Kind.STORY && media.size() != 1) {
            return new Outcome.Failed("una historia lleva un solo archivo en Publora");
        }
        String content = content(p, kind);
        // La misma ronda reintenta con la misma clave: Publora no duplica si la llamada se repite
        String idempotency = UUID.nameUUIDFromBytes((p.getId() + "|" + job.getCreatedAt() + "|" + job.getAttempts())
                .getBytes(StandardCharsets.UTF_8)).toString();
        try {
            String group = publora.schedule(key, account.connectionId(), content, settings(p.getPlatform(), kind),
                    media, p.scheduledAt(), idempotency);
            return new Outcome.Scheduled(group, null);
        } catch (PubloraGateway.PubloraException e) {
            return new Outcome.Failed(e.getMessage());
        }
    }

    @Override
    public Optional<Outcome> check(Publication p, PublishJob job, Instant now) {
        String key = secrets.get(KEY).orElse(null);
        if (key == null || job.getExternalId() == null) return Optional.empty();
        PubloraGateway.PostState state;
        try {
            state = publora.post(key, job.getExternalId());
        } catch (PubloraGateway.PubloraException e) {
            return Optional.of(new Outcome.NotYet(e.getMessage()));
        }
        return Optional.of(switch (state.status().toLowerCase(Locale.ROOT)) {
            case "published" -> state.postedId() == null && state.permalink() == null
                    ? new Outcome.NotYet("publicada sin enlace todavía")
                    : new Outcome.Published(state.postedId(), state.permalink());
            case "failed", "partially_published" -> new Outcome.Failed("Publora no pudo publicarla"
                    + (state.error() == null ? "" : ": " + state.error()));
            case "draft" -> new Outcome.Failed("en Publora quedó como borrador");
            default -> new Outcome.NotYet("Publora: " + state.status());
        });
    }

    @Override
    public boolean unschedule(Publication p, PublishJob job) {
        String key = secrets.get(KEY).orElse(null);
        if (key == null || job.getExternalId() == null) return false;
        publora.delete(key, job.getExternalId());
        return true;
    }

    /** Texto y hashtags. Las historias no llevan texto en Publora. */
    static String content(Publication p, QualityChecker.Kind kind) {
        if (kind == QualityChecker.Kind.STORY) return p.getTitle();
        String caption = p.getCaption() == null ? "" : p.getCaption().strip();
        String tags = p.getHashtags() == null ? "" : p.getHashtags().strip();
        return tags.isEmpty() ? caption : caption.isEmpty() ? tags : caption + "\n\n" + tags;
    }

    static Map<String, Object> settings(Platform platform, QualityChecker.Kind kind) {
        if (platform == Platform.INSTAGRAM) {
            return Map.of("instagram", Map.of("videoType", kind == QualityChecker.Kind.STORY ? "STORIES" : "REELS"));
        }
        return Map.of();
    }

    static String label(Platform platform) { return platform == Platform.INSTAGRAM ? "Instagram" : "TikTok"; }

    private static @org.jspecify.annotations.Nullable String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".mp4") || name.endsWith(".m4v")) return "video/mp4";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".webm")) return "video/webm";
        return null;
    }
}
