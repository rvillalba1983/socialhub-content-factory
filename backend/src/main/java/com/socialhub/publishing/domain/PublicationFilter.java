package com.socialhub.publishing.domain;

import java.time.LocalDate;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Filtros del calendario de publicaciones de una promoción. Todos opcionales salvo la promoción. */
public record PublicationFilter(String promotionId, Set<PublicationStatus> statuses, @Nullable Platform platform,
                                @Nullable LocalDate from, @Nullable LocalDate to, @Nullable Boolean flagged) {}
