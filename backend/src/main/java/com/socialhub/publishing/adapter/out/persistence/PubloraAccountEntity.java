package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Platform;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tabla {@code brand_publora_account}. */
@Entity
@Table(name = "brand_publora_account")
@IdClass(PubloraAccountEntity.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
class PubloraAccountEntity {

    @Id private String brandId;
    @Id @Enumerated(EnumType.STRING) private Platform platform;
    private String connectionId;
    private String label;

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String brandId;
        private Platform platform;
    }
}
