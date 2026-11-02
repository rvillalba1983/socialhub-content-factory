package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.jspecify.annotations.Nullable;

/** Tabla {@code publication}. Envers guarda en {@code publication_aud} el estado, la incidencia y la confirmación. */
@Entity
@Table(name = "publication")
@Audited
@Getter
@Setter
@NoArgsConstructor
class PublicationEntity {

    @Id
    private String id;

    @NotAudited private String promotionId;

    @NotAudited
    @Enumerated(EnumType.STRING)
    private Platform platform;

    @NotAudited private String type;
    @NotAudited private String title;
    @NotAudited private LocalDate scheduledOn;
    @NotAudited private LocalTime scheduledTime;
    @NotAudited private String timezone;

    @Enumerated(EnumType.STRING)
    private PublicationStatus status;

    @NotAudited private String route;
    private @Nullable String flag;
    @NotAudited private @Nullable String note;
    private @Nullable String externalId;
    private @Nullable String confirmation;
    private @Nullable Instant publishedAt;

    @NotAudited
    @Column(name = "is_demo")
    private boolean demo;

    @Version
    private long version;
}
