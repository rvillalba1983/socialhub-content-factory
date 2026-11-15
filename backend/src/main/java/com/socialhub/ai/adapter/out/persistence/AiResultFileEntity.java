package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiResultFile;
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

    AiResultFileEntity(AiResultFile file) {
        this.path = file.path();
        this.modifiedAt = file.modifiedAt();
        this.requestId = file.requestId();
        this.outcome = file.outcome();
        this.note = file.note();
        this.readAt = file.readAt();
    }
}
