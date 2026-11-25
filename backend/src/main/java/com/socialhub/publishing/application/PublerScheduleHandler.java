package com.socialhub.publishing.application;

import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationSound;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.settings.PublishSettings;
import java.time.Clock;
import com.socialhub.publishing.domain.PublicationFileRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * «Programar en Publer»: Claude, con Chrome en el PC de Raúl, programa la publicación en la web de Publer y devuelve
 * {@code outputs.scheduled = true}, la hora con la que quedó y, si lo ve, el identificador de Publer.
 */
@Component
class PublerScheduleHandler implements AiTypeHandler {

    /** Diferencia admitida entre la hora pedida y la que dice Claude que quedó. */
    static final Duration TOLERANCE = Duration.ofMinutes(1);

    private final PublishingQueriesService queries;
    private final PublicationFileRepository files;
    private final PublishingEngine engine;
    private final PublerAccountRepository accounts;
    private final PublishSettings settings;
    private final Clock clock;

    PublerScheduleHandler(PublishingQueriesService queries, PublicationFileRepository files, PublishingEngine engine,
                          PublerAccountRepository accounts, PublishSettings settings, Clock clock) {
        this.queries = queries;
        this.files = files;
        this.engine = engine;
        this.accounts = accounts;
        this.settings = settings;
        this.clock = clock;
    }

    @Override public AiRequestType type() { return AiRequestType.PUBLER_SCHEDULE; }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.size() != 1 || queries.find(inputs.getFirst()).filter(p -> p.getPromotionId().equals(promotionId)).isEmpty()) {
            throw new IllegalArgumentException("El encargo lleva una publicación de la promoción");
        }
    }

    /**
     * Lo único que Claude escribe en Publer: texto, hashtags y archivos, y a qué hora. Sin el título de trabajo, sin la nota
     * interna, sin el aviso ni el sonido (hallazgo C y 23-16): nada de lo que se escribe en la red sale de ahí.
     */
    static Map<String, Object> scheduleView(Publication p, PublicationFileRepository files) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", p.getId());
        m.put("platform", p.getPlatform().name());
        m.put("type", p.getType());
        if (p.getCaption() != null) m.put("caption", p.getCaption());
        if (p.getHashtags() != null) m.put("hashtags", p.getHashtags());
        m.put("scheduled_at", p.scheduledAt().toString());
        m.put("timezone", p.getTimezone());
        m.put("files", files.files(p.getId()).stream().map(f -> f.path()).toList());
        return m;
    }

    static Map<String, Object> publication(Publication p, PublicationFileRepository files) {
        var m = new java.util.LinkedHashMap<>(PublishingQueriesService.forAi(p));
        m.put("scheduled_at", p.scheduledAt().toString());
        m.put("timezone", p.getTimezone());
        m.put("files", files.files(p.getId()).stream().map(f -> f.path()).toList());
        return m;
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        Publication p = queries.find(job.inputs().getFirst()).orElseThrow();
        String account = accounts.account(job.brandId(), p.getPlatform()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publication", scheduleView(p, files));
        m.put("publer_account", account);
        List<String> how = new ArrayList<>();
        if (account == null) {
            how.add("La marca no tiene indicada su cuenta de Publer para " + p.getPlatform() + ": no programes nada y devuelve status failed con ese motivo.");
        } else {
            how.add("Abre Publer (publer.com) en Chrome con la sesión de Raúl; no inicies sesión por él ni crees cuentas.");
            how.add("Crea una publicación " + inAccount(account) + " (" + p.getPlatform() + ") con el texto («caption»), los hashtags y los archivos de «files», en ese orden.");
            how.add("Prográmala para «scheduled_at» (hora de " + p.getTimezone() + "). No la publiques al momento.");
            if (p.getPlatform() == Platform.TIKTOK) how.addAll(tiktokOptions(p.getSound()));
            else if (p.getSound().mode() == PublicationSound.Mode.NETWORK) how.add(networkSound(p.getSound(), p.getPlatform().name()));
            Duration margin = settings.minMarginFor(p.getPlatform().name());
            m.put("min_publish_margin_minutes", margin.toMinutes());
            how.add("Margen mínimo: «scheduled_at» tiene que quedar al menos " + margin.toMinutes() + " minutos por delante del momento en que lo hagas; "
                    + "si no, no programes nada y devuelve status failed con el motivo (nunca se publica al instante).");
            how.add("Antes de terminar, comprueba en el calendario de Publer, en esa cuenta, que aparece a esa hora y no está duplicada.");
            how.add("Si pasó la hora límite («deadline») o falta algo, no programes nada: devuelve status failed con el motivo.");
        }
        m.put("how", how);
        m.put("expected_outputs", "scheduled (true), scheduled_for (ISO 8601 con zona), publer_id si se ve, url si la hay, summary");
        return m;
    }

    /** «en la cuenta «X» de Publer». */
    static String inAccount(String account) { return "en la cuenta «" + account + "» de Publer"; }

    /** Opciones de TikTok al programar en Publer (cuenta personal, públicas desde la primera). */
    static List<String> tiktokOptions(PublicationSound sound) {
        List<String> options = new ArrayList<>(List.of(
                "TikTok, privacidad: «Todo el mundo» (pública desde la primera publicación; no «Solo yo» ni «Amigos»).",
                "TikTok, interacción: deja activados Comentarios, Dúo y Stitch."));
        if (sound.mode() == PublicationSound.Mode.NETWORK) {
            options.add(networkSound(sound, "TikTok"));
        } else {
            options.add("TikTok, sonido: los sonidos ya van en el vídeo (o no lleva); no añadas música ni sonidos de la biblioteca de TikTok.");
        }
        return options;
    }

    /** El sonido se elige en la red, no en Publer: Claude no lo añade, pero deja dicho que falta (decisión 23). */
    static String networkSound(PublicationSound sound, String network) {
        boolean named = sound.track() != null && !sound.track().isBlank();
        String warning = named ? "Falta elegir el sonido «" + sound.track() + "» en " + network : "Falta elegir el sonido en " + network;
        return network + ", sonido: NO añadas música ni sonidos desde Publer. Raúl elegirá en la propia red el sonido"
                + (named ? " «" + sound.track() + "» (dato interno: no es texto público, no lo escribas en el texto ni en los hashtags)"
                        : " (aún sin elegir el tema)")
                + ". Al terminar, deja en «outputs.note» «" + warning + "» y en «warnings» el mismo aviso.";
    }

    /** Pasada esta hora Claude no debe programar: 8 h antes (Publer) o, si es antes, el margen mínimo de publicación de la red. */
    @Override
    public @Nullable Instant deadline(AiJob job) {
        return queries.find(job.inputs().getFirst())
                .map(p -> PublerRoute.limit(p.scheduledAt(), settings.minMarginFor(p.getPlatform().name()))).orElse(null);
    }

    /**
     * Ya no hace falta enviarlo: la publicación ya no existe o está cancelada, su encargo vigente es otro (una sola programación
     * viva por publicación) o ya no queda el margen mínimo de publicación. SocialHub lo cancela en vez de enviarlo.
     */
    @Override
    public boolean obsolete(AiJob job) {
        Publication p = queries.find(job.inputs().getFirst()).orElse(null);
        if (p == null || p.getStatus() == PublicationStatus.CANCELLED) return true;
        var current = engine.job(p.getId());
        if (current.isPresent() && (!current.get().getStatus().open() || !job.taskId().equals(current.get().getTaskId()))) return true;
        return !clock.instant().isBefore(p.scheduledAt().minus(settings.minMarginFor(p.getPlatform().name())));
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        String id = job.inputs().getFirst();
        Publication p = queries.find(id).orElseThrow(() -> new IllegalArgumentException("Ya no existe la publicación " + id));
        if (!outputs.path("scheduled").asBoolean(false)) {
            throw new IllegalArgumentException("Falta «outputs.scheduled: true»: si no se pudo programar, el estado es failed");
        }
        String when = outputs.path("scheduled_for").asString("");
        if (when.isBlank()) throw new IllegalArgumentException("Falta «outputs.scheduled_for» (la hora con la que quedó en Publer)");
        Instant at;
        try {
            at = java.time.OffsetDateTime.parse(when).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("«scheduled_for» no es una fecha ISO 8601 con zona: " + when);
        }
        if (Duration.between(at, p.scheduledAt()).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalArgumentException("Quedó programada a otra hora (" + when + "; tocaba " + p.scheduledAt()
                    + "). Revisa Publer y corrígela allí");
        }
        String publerId = outputs.path("publer_id").asString("");
        String url = outputs.path("url").asString("");
        String done = engine.scheduled(id, job.taskId(), publerId.isBlank() ? null : publerId, url.isBlank() ? null : url);
        // El sonido se elige en la red (decisión 23): el aviso para Raúl viaja en el resultado de la tarea
        if (p.getSound().mode() == PublicationSound.Mode.NETWORK) {
            String track = p.getSound().track();
            done += " · Falta elegir el sonido" + (track == null || track.isBlank() ? "" : " «" + track + "»") + " en "
                    + (p.getPlatform() == Platform.TIKTOK ? "TikTok" : "la red");
        }
        return done;
    }

    @Override
    public void onCancelled(AiJob job, String reason) {
        engine.taskCancelled(job.inputs().getFirst(), job.taskId(), reason);
    }

    @Override
    public @Nullable String late(AiJob job, JsonNode outputs, JsonNode result) {
        // Si Claude no llegó a programarla («scheduled» no es true), no hay nada que borrar en Publer
        if (!outputs.path("scheduled").asBoolean(false)) return null;
        return engine.late(job.inputs().getFirst(), job.taskId());
    }

    @Override
    public void onFailed(AiJob job, String reason) {
        engine.routeFailed(job.inputs().getFirst(), job.taskId(), reason);
    }
}
