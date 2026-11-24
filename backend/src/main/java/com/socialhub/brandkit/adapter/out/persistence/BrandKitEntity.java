package com.socialhub.brandkit.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    /**
     * Versión de la fila: si dos transacciones cambian el mismo kit a la vez (aprobarlo mientras llega otro nuevo que lo
     * sustituye), la segunda falla en lugar de pisar a la primera y dejar la marca sin kit aprobado.
     */
    @Version
    @Column(name = "row_version")
    private long rowVersion;
}
