package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.publishing.domain.PublicMediaHost;
import com.socialhub.publishing.domain.PublicMediaRepository;
import com.socialhub.publishing.domain.Publication;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * «Enlaces públicos»: SocialHub deja las imágenes de una publicación de Instagram en la carpeta pública de Drive y
 * Claude, en la nube y con su conector de Drive, busca cada archivo por su nombre y devuelve su enlace de descarga
 * ({@code outputs.links: [{file, id}]}, y SocialHub construye el enlace). Con los enlaces, la publicación queda programada en la API oficial (que la
 * publica a la hora). Si algo no sirve antes del corte previo, la publicación sigue por Publer, que aún tiene tiempo.
 */
@Component
class PublicLinkHandler implements AiTypeHandler {

    static final String FOLDER = "publico";
    /** El enlace de descarga de un archivo de Drive: lo construye SocialHub con el id, nunca se acepta uno que traiga Claude. */
    static final String DRIVE_LINK = "https://drive.google.com/uc?export=download&id=";
    private static final java.util.regex.Pattern DRIVE_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{10,}");

    private final PublicMediaRepository media;
    private final PublicMediaHost host;
    private final PublishingQueries queries;
    private final PublishingEngine engine;
    private final Clock clock;
    private final String linkBase;

    PublicLinkHandler(PublicMediaRepository media, PublicMediaHost host, PublishingQueries queries, @Lazy PublishingEngine engine,
                      Clock clock,
                      @Value("${socialhub.publishing.public-link-base:" + DRIVE_LINK + "}") String linkBase) {
        this.media = media;
        this.host = host;
        this.queries = queries;
        this.engine = engine;
        this.clock = clock;
        this.linkBase = linkBase;
    }

    @Override public AiRequestType type() { return AiRequestType.PUBLIC_LINK; }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.isEmpty()) throw new IllegalArgumentException("El encargo lleva los nombres de los archivos de «publico»");
        String publication = null;
        for (String name : inputs) {
            var item = media.byFileName(name).orElseThrow(() -> new IllegalArgumentException("No hay ninguna imagen pública «" + name + "»"));
            if (publication != null && !publication.equals(item.publicationId())) {
                throw new IllegalArgumentException("Los archivos son de publicaciones distintas");
            }
            publication = item.publicationId();
            if (queries.find(publication).filter(p -> p.getPromotionId().equals(promotionId)).isEmpty()) {
                throw new IllegalArgumentException("La publicación no es de la promoción");
            }
        }
    }

    @Override
    public @Nullable Instant deadline(AiJob job) {
        return publication(job).map(p -> InstagramRoute.cutoff(p.scheduledAt())).orElse(null);
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("files", job.inputs());
        m.put("folder", FOLDER);
        m.put("how", List.of(
                "Con tu conector de Google Drive, busca dentro de la carpeta «" + FOLDER + "» (dentro de la carpeta de SocialHub) cada archivo de «files» por su nombre exacto.",
                "Para cada uno devuelve su id de Drive (solo el id: SocialHub construye el enlace).",
                "Solo lectura: no muevas, no borres, no compartas ni cambies permisos de nada. Si un archivo no aparece, no lo inventes: devuelve status \"failed\" y di cuál falta."));
        m.put("expected_outputs", "links: [{file (nombre del archivo), id (id de Drive)}] con un elemento por archivo de «files», y summary");
        return m;
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        JsonNode links = outputs.path("links");
        if (!links.isArray()) throw new IllegalArgumentException("Falta «outputs.links» (una lista con un enlace por archivo)");
        Map<String, String> urls = new LinkedHashMap<>();
        for (String name : job.inputs()) {
            String id = null;
            for (JsonNode l : links) {
                if (!name.equals(l.path("file").asString("").strip())) continue;
                id = l.path("id").asString("").strip();
                break;
            }
            if (id == null) throw new IllegalArgumentException("Falta el enlace de " + name);
            if (!DRIVE_ID.matcher(id).matches()) throw new IllegalArgumentException("El id de Drive de " + name + " no es válido");
            urls.put(name, linkBase + id);
        }
        // Antes de dar nada por bueno, cada enlace tiene que devolver una imagen
        for (var e : urls.entrySet()) {
            Optional<String> problem = host.problem(URI.create(e.getValue()));
            if (problem.isPresent()) throw new IllegalArgumentException("El enlace de " + e.getKey() + " no sirve: " + problem.get());
        }
        String publicationId = media.byFileName(job.inputs().getFirst()).map(PublicMediaRepository.Item::publicationId)
                .orElseThrow(() -> new IllegalArgumentException("Ya no hay imágenes públicas para este encargo"));
        boolean current = engine.job(publicationId)
                .filter(j -> job.taskId().equals(j.getTaskId()) && "API oficial".equals(j.getRoute())).isPresent();
        if (!current) {
            engine.discardPublicMedia(publicationId);
            return "Enlaces llegados tarde: la publicación ya sigue por otra vía y no hacen falta";
        }
        // Llegados después del corte previo, ya no sirven: Publer necesita su margen
        boolean late = queries.find(publicationId).map(p -> !clock.instant().isBefore(InstagramRoute.cutoff(p.scheduledAt()))).orElse(true);
        if (late) {
            engine.discardPublicMedia(publicationId);
            return "Enlaces llegados tarde, pasado el corte previo: la publicación sigue por Publer";
        }
        urls.forEach((name, url) -> media.setUrl(publicationId, name, url));
        return engine.scheduled(publicationId, job.taskId(), null, null);
    }

    @Override
    public void onFailed(AiJob job, String reason) {
        publication(job).ifPresent(p -> engine.routeFailed(p.getId(), job.taskId(), reason));
    }

    private Optional<Publication> publication(AiJob job) {
        return job.inputs().stream().findFirst().flatMap(media::byFileName).flatMap(i -> queries.find(i.publicationId()));
    }

}
