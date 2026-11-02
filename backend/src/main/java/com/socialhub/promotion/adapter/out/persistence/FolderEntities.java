package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.domain.FolderRole;
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

/** Tablas {@code brand_folder} y {@code promotion_folder}. */
final class FolderEntities {

    private FolderEntities() {}

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class BrandFolderId implements Serializable {
        private String brandId;
        private FolderRole role;
    }

    @Entity
    @Table(name = "brand_folder")
    @IdClass(BrandFolderId.class)
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class BrandFolderEntity {
        @Id private String brandId;
        @Id @Enumerated(EnumType.STRING) private FolderRole role;
        private String location;
    }

    @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
    static class PromotionFolderId implements Serializable {
        private String promotionId;
        private FolderRole role;
    }

    @Entity
    @Table(name = "promotion_folder")
    @IdClass(PromotionFolderId.class)
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class PromotionFolderEntity {
        @Id private String promotionId;
        @Id @Enumerated(EnumType.STRING) private FolderRole role;
        private String location;
    }
}
