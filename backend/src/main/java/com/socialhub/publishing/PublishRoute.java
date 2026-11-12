package com.socialhub.publishing;

import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublishJob;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Una vía de publicación (Publer, Publora o API oficial). El motor las prueba por orden; cada vía dice si está
 * configurada, hasta cuándo sirve y qué ha pasado al intentar programar.
 */
public interface PublishRoute {

    enum Id {
        PUBLER("Publer"), PUBLORA("Publora"), OFFICIAL("API oficial");

        private final String label;

        Id(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** Resultado de un intento. */
    sealed interface Outcome {
        /** Programada en la vía. */
        record Scheduled(@Nullable String externalId, @Nullable String url) implements Outcome {}
        /** Espera un encargo a Claude: el resultado llega después. */
        record Pending(String taskId) implements Outcome {}
        /** Ya está publicada (solo al comprobar). */
        record Published(@Nullable String externalId, @Nullable String url) implements Outcome {}
        record Failed(String reason) implements Outcome {}
        /**
         * La vía no puede programarla todavía y no hay que seguir con otra (falta un dato de la marca o el plan de la vía
         * está al límite): la publicación queda retenida con este motivo y se vuelve a probar sola.
         */
        record Held(String reason) implements Outcome {}
        /** Al comprobar: aún no se sabe (la vía sigue publicando). Se vuelve a mirar más tarde. */
        record NotYet(String reason) implements Outcome {}
    }

    Id id();

    /** Está lista para usarse (por ejemplo, tiene su clave). */
    boolean configured();

    /** Último momento en que tiene sentido seguir con esta vía para una publicación a esa hora. */
    Instant lastMoment(Instant scheduledAt);

    /** Intenta programarla. Solo se llama si está configurada y antes de {@link #lastMoment}. */
    Outcome schedule(Publication publication, PublishJob job, Instant now);

    /**
     * Comprueba si ya está publicada. Vacío: la comprueba Claude (en Publer o en la red), que es lo que hacen las vías
     * sin API.
     */
    default Optional<Outcome> check(Publication publication, PublishJob job, Instant now) { return Optional.empty(); }

    /**
     * Quita de la vía lo que programó SocialHub (Raúl la ha sacado de «Programada»).
     *
     * @return {@code true} si la ha quitado; {@code false} si hay que quitarla a mano
     */
    default boolean unschedule(Publication publication, PublishJob job) { return false; }
}
