package com.socialhub.pieces.adapter.out.persistence;

import com.socialhub.pieces.domain.PieceOrder;
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

    PieceOrderEntity(PieceOrder order) {
        this.promotionId = order.promotionId();
        this.specPath = order.specPath();
        this.specModifiedAt = order.specModifiedAt();
        this.taskId = order.taskId();
        this.createdAt = order.createdAt();
    }

    PieceOrder toDomain() { return new PieceOrder(promotionId, specPath, specModifiedAt, taskId, createdAt); }
}
