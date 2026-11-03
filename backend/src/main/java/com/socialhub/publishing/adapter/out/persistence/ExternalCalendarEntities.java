package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.ExternalCalendarRepository;
import com.socialhub.publishing.domain.Platform;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Tablas {@code external_post} y {@code external_calendar_state}. */
final class ExternalCalendarEntities {

    private ExternalCalendarEntities() {}

    @Entity(name = "ExternalPostEntity") @Table(name = "external_post")
    @Getter @NoArgsConstructor
    static class PostEntity {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private String brandId;
        @Enumerated(EnumType.STRING) private ExternalCalendarRepository.Source source;
        @Enumerated(EnumType.STRING) private Platform platform;
        private Instant at;
        @Enumerated(EnumType.STRING) private ExternalCalendarRepository.Status status;
        private @Nullable String text;
        private @Nullable String url;
        private @Nullable String externalId;
        private @Nullable String publicationId;
        private Instant seenAt;

        PostEntity(String brandId, ExternalCalendarRepository.Item i, Instant seenAt) {
            this.brandId = brandId;
            this.source = i.source();
            this.platform = i.platform();
            this.at = i.at();
            this.status = i.status();
            this.text = i.text();
            this.url = i.url();
            this.externalId = i.externalId();
            this.publicationId = i.publicationId();
            this.seenAt = seenAt;
        }
    }

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class StateKey implements Serializable {
        private String brandId;
        private ExternalCalendarRepository.Source source;
    }

    @Entity(name = "ExternalCalendarStateEntity") @Table(name = "external_calendar_state") @IdClass(StateKey.class)
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class StateEntity {
        @Id private String brandId;
        @Id @Enumerated(EnumType.STRING) private ExternalCalendarRepository.Source source;
        private @Nullable LocalDate requestedOn;
        private @Nullable String taskId;
        private @Nullable Instant syncedAt;
        private @Nullable String error;
    }
}
