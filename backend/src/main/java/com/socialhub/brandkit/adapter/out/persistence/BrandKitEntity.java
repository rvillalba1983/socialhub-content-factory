package com.socialhub.brandkit.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/** Una versión del kit (tabla {@code brand_kit}). */
@Entity
@Table(name = "brand_kit")
@Getter
@Setter
@NoArgsConstructor
class BrandKitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    private String brandId;
    private int version;
    private String status;
    private String kitJson;
    private @Nullable String comment;
    private @Nullable String sourceTaskId;
    private Instant createdAt;
    private @Nullable Instant approvedAt;
}
