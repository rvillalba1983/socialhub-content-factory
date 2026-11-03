package com.socialhub.publishing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Lo programado o publicado fuera de SocialHub (Publora, Publer y la red), por marca. */
@SecondaryPort
public interface ExternalCalendarRepository {

    enum Source {
        PUBLORA("Publora"), PUBLER("Publer"), NETWORK("La red");

        private final String label;

        Source(String label) { this.label = label; }

        public String label() { return label; }
    }

    enum Status { SCHEDULED, PUBLISHED, FAILED }

    record Item(Source source, Platform platform, Instant at, Status status, @Nullable String text, @Nullable String url,
                @Nullable String externalId, @Nullable String publicationId) {}

    record State(Source source, @Nullable LocalDate requestedOn, @Nullable String taskId, @Nullable Instant syncedAt,
                 @Nullable String error) {}

    /** Sustituye lo de esa fuente y marca entre {@code from} y {@code to}. */
    void replace(String brandId, Source source, Instant from, Instant to, List<Item> items, Instant seenAt);

    List<Item> between(String brandId, Instant from, Instant to);

    Optional<State> state(String brandId, Source source);

    List<State> states(String brandId);

    void saveState(String brandId, State state);
}
