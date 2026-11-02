package com.socialhub.ai;

import jakarta.persistence.Entity;
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

/** Un encargo semanal ya creado (tabla {@code ai_auto_order}). */
@Entity
@Table(name = "ai_auto_order")
@IdClass(AiAutoOrderEntity.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
class AiAutoOrderEntity {

    @Id private String promotionId;
    @Id private String type;
    @Id private LocalDate weekStart;
    private String taskId;
    private Instant createdAt;

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String promotionId;
        private String type;
        private LocalDate weekStart;
    }
}
