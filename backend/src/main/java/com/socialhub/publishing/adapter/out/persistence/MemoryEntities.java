package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.EditorialMemoryRepository;
import com.socialhub.publishing.domain.RejectionReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Tablas {@code editorial_rejection} y {@code text_correction}. */
final class MemoryEntities {

    private MemoryEntities() {}

    @Entity @Table(name = "editorial_rejection")
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class RejectionEntity {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private @Nullable Long id;
        private String brandId;
        private @Nullable String promotionId;
        private @Nullable String publicationId;
        @Enumerated(EnumType.STRING) private EditorialMemoryRepository.Kind kind;
        @Enumerated(EnumType.STRING) private RejectionReason reason;
        private @Nullable String note;
        private String title;
        private @Nullable String content;
        private Instant createdAt;
    }

    @Entity @Table(name = "text_correction")
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class CorrectionEntity {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private @Nullable Long id;
        private String brandId;
        private @Nullable String promotionId;
        private @Nullable String publicationId;
        private @Nullable String beforeCaption;
        private @Nullable String afterCaption;
        private @Nullable String beforeHashtags;
        private @Nullable String afterHashtags;
        private Instant createdAt;
    }
}
