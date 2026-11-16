package com.socialhub.promotion;

import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.BrandIdentity;
import com.socialhub.promotion.domain.EditorialDecision;
import com.socialhub.promotion.domain.FolderAssignment;
import com.socialhub.promotion.domain.FolderRole;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionDraft;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Alta y edición de marcas y promociones. Lo creado en modo demo queda marcado como ejemplo. */
@PrimaryPort
public interface PromotionCommands {

    Brand createBrand(BrandDraft draft);

    Brand updateBrand(String id, BrandDraft draft);

    Promotion createPromotion(PromotionDraft draft);

    Promotion updatePromotion(String id, PromotionDraft draft);

    /** Sustituye los papeles de carpeta de la marca. Las promociones los heredan. */
    List<FolderAssignment> replaceBrandFolders(String brandId, Map<FolderRole, String> assignments);

    /**
     * Carpetas de la marca. Con carpeta raíz: Material es la propia raíz, el resto son subcarpetas suyas en minúsculas
     * (borradores, generados, aprobados, publicados, informes, encargos) y dentro de encargos se crean para-claude,
     * para-claude-pc, de-claude y publico. Los 7 papeles pasan a apuntar ahí. Sin raíz se crean las carpetas ya asignadas.
     * Nunca se borra ni se mueve nada: lo que ya exista se respeta.
     */
    List<FolderAssignment> createBrandFolders(String brandId, @org.jspecify.annotations.Nullable String base);

    /** Si Raúl marcó la carpeta «publico» como compartida y la carpeta de Encargos sigue siendo la misma. */
    boolean publicShared(String brandId);

    /** Marca (con la carpeta de Encargos de ahora) o desmarca que la carpeta «publico» ya está compartida. */
    boolean setPublicShared(String brandId, boolean shared);

    /** Sustituye las excepciones de la promoción. Devuelve los papeles que quedan en vigor. */
    List<FolderAssignment> replacePromotionFolders(String promotionId, Map<FolderRole, String> assignments);

    /** Guarda la identidad de la marca. Sus promociones la heredan. */
    BrandIdentity saveIdentity(String brandId, BrandIdentity identity);

    EditorialDecision addDecision(String brandId, String text);

    void deleteDecision(String brandId, long id);
}
