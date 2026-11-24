package com.socialhub.brandkit.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Una versión del kit de una marca. {@code kitJson} es el kit tal como se guarda (JSON normalizado); {@code kit} es el mismo
 * contenido ya leído. {@code comment} es el de «Pedir otra versión».
 */
public record BrandKit(@Nullable Long id, String brandId, int version, KitStatus status, KitContent kit, String kitJson,
                       @Nullable String comment, @Nullable String sourceTaskId, Instant createdAt,
                       @Nullable Instant approvedAt) {

    /** Lo que se le dice a Raúl cuando actúa sobre un kit que ya no es el pendiente (llegó otro, ya se aprobó o se rechazó). */
    public static final String NOT_PENDING = "Ese kit ya no es el pendiente; recarga.";

    public BrandKit withStatus(KitStatus newStatus, @Nullable String newComment, @Nullable Instant newApprovedAt) {
        return new BrandKit(id, brandId, version, newStatus, kit, kitJson, newComment, sourceTaskId, createdAt, newApprovedAt);
    }
}
