package com.socialhub.promotion.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Tabla {@code brand_decision}. */
@Entity
@Table(name = "brand_decision")
@Getter
@NoArgsConstructor
class BrandDecisionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private @Nullable Long id;
    private String brandId;
    private String text;
    private Instant createdAt;

    BrandDecisionEntity(String brandId, String text, Instant createdAt) {
        this.brandId = brandId;
        this.text = text;
        this.createdAt = createdAt;
    }
}
