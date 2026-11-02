package com.socialhub.promotion.domain;

import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Papeles de carpeta de marcas y promociones. */
@SecondaryPort
public interface FolderRepository {

    Map<FolderRole, String> brandFolders(String brandId);

    Map<FolderRole, String> promotionFolders(String promotionId);

    /** Sustituye todos los papeles de la marca. */
    void replaceBrandFolders(String brandId, Map<FolderRole, String> folders);

    /** Sustituye las excepciones de la promoción. */
    void replacePromotionFolders(String promotionId, Map<FolderRole, String> folders);
}
