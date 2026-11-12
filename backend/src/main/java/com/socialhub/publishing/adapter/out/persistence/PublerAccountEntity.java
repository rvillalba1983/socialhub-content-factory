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

/** Tabla {@code brand_publer_account}. */
@Entity
@Table(name = "brand_publer_account")
@IdClass(PublerAccountEntity.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
class PublerAccountEntity {

    @Id private String brandId;
    @Id @Enumerated(EnumType.STRING) private Platform platform;
    private String accountName;

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class Key implements Serializable {
        private String brandId;
        private Platform platform;
    }
}
