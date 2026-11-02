package com.socialhub.pieces;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Un spec.json que ya se ha encargado (tabla {@code piece_order}). */
@Entity
@Table(name = "piece_order")
@Getter
@NoArgsConstructor
class PieceOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    private String promotionId;
    private String specPath;
    private Instant specModifiedAt;
    private @Nullable String taskId;
    private Instant createdAt;

    PieceOrderEntity(String promotionId, String specPath, Instant specModifiedAt, String taskId, Instant createdAt) {
        this.promotionId = promotionId;
        this.specPath = specPath;
        this.specModifiedAt = specModifiedAt;
        this.taskId = taskId;
        this.createdAt = createdAt;
    }
}
