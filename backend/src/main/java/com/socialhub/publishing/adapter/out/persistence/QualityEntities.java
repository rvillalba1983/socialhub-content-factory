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
import org.jspecify.annotations.Nullable;

/** Tablas {@code publication_quality_ack} y {@code publication_review_issue}. */
final class QualityEntities {

    private QualityEntities() {}

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String publicationId;
        private String issueKey;
    }

    @Entity @Table(name = "publication_quality_ack") @IdClass(Key.class)
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class AckEntity {
        @Id private String publicationId;
        @Id private String issueKey;
        private String reason;
        private Instant acceptedAt;
    }

    @Entity @Table(name = "publication_review_issue") @IdClass(Key.class)
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class ReviewIssueEntity {
        @Id private String publicationId;
        @Id private String issueKey;
        private String message;
        private @Nullable String taskId;
        private Instant createdAt;
    }
}
