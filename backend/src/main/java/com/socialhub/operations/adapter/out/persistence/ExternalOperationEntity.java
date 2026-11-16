package com.socialhub.operations.adapter.out.persistence;

import com.socialhub.operations.domain.ExternalOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Fila de {@code external_operation}. */
@Entity
@Table(name = "external_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class ExternalOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    private String system;
    private String operation;

    @Enumerated(EnumType.STRING)
    private ExternalOperation.Outcome outcome;

    private Instant startedAt;
    private long durationMs;
    private @Nullable String traceId;
    private @Nullable String errorType;

    @Column(length = 2000)
    private @Nullable String errorMessage;

    private @Nullable String detail;

    static ExternalOperationEntity of(ExternalOperation o) {
        return new ExternalOperationEntity(o.id(), o.system(), o.operation(), o.outcome(), o.startedAt(), o.durationMs(),
                o.traceId(), o.errorType(), o.errorMessage(), o.detail());
    }

    ExternalOperation toDomain() {
        return new ExternalOperation(id, system, operation, outcome, startedAt, durationMs, traceId, errorType,
                errorMessage, detail);
    }
}
