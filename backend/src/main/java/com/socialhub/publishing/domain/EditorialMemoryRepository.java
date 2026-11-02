package com.socialhub.publishing.domain;

import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jspecify.annotations.Nullable;

/** Rechazos y correcciones de Raúl (memoria editorial). */
@SecondaryPort
public interface EditorialMemoryRepository {

    enum Kind { PUBLICATION, TEXT }

    record Rejection(String brandId, @Nullable String promotionId, @Nullable String publicationId, Kind kind,
                     RejectionReason reason, @Nullable String note, String title, @Nullable String content, Instant at) {}

    record Correction(String brandId, @Nullable String promotionId, @Nullable String publicationId,
                      @Nullable String beforeCaption, @Nullable String afterCaption, @Nullable String beforeHashtags,
                      @Nullable String afterHashtags, Instant at) {}

    void add(Rejection rejection);

    void add(Correction correction);

    /** Las más recientes primero. */
    List<Rejection> rejections(String brandId, int limit);

    /** Las más recientes primero. */
    List<Correction> corrections(String brandId, int limit);
}
