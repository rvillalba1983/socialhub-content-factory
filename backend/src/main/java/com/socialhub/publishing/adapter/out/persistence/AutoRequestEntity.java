package com.socialhub.publishing.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tabla {@code auto_ai_request}. */
@Entity
@Table(name = "auto_ai_request")
@IdClass(AutoRequestEntity.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
class AutoRequestEntity {

    @Id private String publicationId;
    @Id private String kind;
    @Id private String fingerprint;
    private String taskId;
    private Instant createdAt;

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String publicationId;
        private String kind;
        private String fingerprint;
    }
}
