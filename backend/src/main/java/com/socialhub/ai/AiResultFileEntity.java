package com.socialhub.ai;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Un archivo de resultado ya leído (tabla {@code ai_result_file}). */
@Entity
@Table(name = "ai_result_file")
@Getter
@NoArgsConstructor
class AiResultFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    private String path;
    private Instant modifiedAt;
    private @Nullable String requestId;
    private String outcome;
    private @Nullable String note;
    private Instant readAt;

    AiResultFileEntity(String path, Instant modifiedAt, @Nullable String requestId, String outcome,
                       @Nullable String note, Instant readAt) {
        this.path = path;
        this.modifiedAt = modifiedAt;
        this.requestId = requestId;
        this.outcome = outcome;
        this.note = note;
        this.readAt = readAt;
    }
}
