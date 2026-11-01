package com.socialhub.operations;

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

/** Una llamada a un sistema externo y su resultado. Solo se añaden filas; nunca se modifican. */
@Entity
@Table(name = "external_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class ExternalOperation {

    enum Outcome { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    private String system;
    private String operation;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private Instant startedAt;
    private long durationMs;
    private @Nullable String traceId;
    private @Nullable String errorType;

    @Column(length = 2000)
    private @Nullable String errorMessage;

    private @Nullable String detail;
}
