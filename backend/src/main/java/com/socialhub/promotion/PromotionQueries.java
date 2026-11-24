package com.socialhub.promotion;

import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandIdentity;
import com.socialhub.promotion.domain.EditorialDecision;
import com.socialhub.promotion.domain.FolderAssignment;
import com.socialhub.promotion.domain.Promotion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Consultas de marcas y promociones (solo lectura). */
@PrimaryPort
public interface PromotionQueries {

    List<Brand> brands();

    List<Promotion> promotions();

    Promotion promotion(String id);

    List<String> commonRules();

    /**
     * Carpetas donde las piezas pueden leer y escribir: las de «Recursos» de tipo Carpeta y las que tienen un papel,
     * sin repetir y en ese orden.
     */
    List<String> driveFolders(String promotionId);

    /** Papeles de carpeta de la marca. */
    List<FolderAssignment> brandFolders(String brandId);

    /** Papeles en vigor de una promoción: los de la marca, salvo los que la promoción cambia. */
    List<FolderAssignment> folders(String promotionId);

    /** Todas las carpetas configuradas: las de las marcas y las de cada promoción, sin repetir. */
    List<String> allFolders();

    /** Promociones activas cuyas fechas se cruzan con ese intervalo (incluido). */
    List<String> activeBetween(java.time.LocalDate from, java.time.LocalDate to);

    /**
     * Promociones que reciben propuestas (activas o planificadas: una planificada se activa sola el día que empieza)
     * cuyas fechas se cruzan con ese intervalo (incluido). Las pausadas y las terminadas no reciben nada.
     */
    List<String> proposableBetween(java.time.LocalDate from, java.time.LocalDate to);

    /** Fechas de una promoción. */
    record PromotionDates(String id, java.time.LocalDate startsOn, java.time.LocalDate endsOn) {}

    /** Promociones activas o planificadas, con sus fechas (las que reciben propuestas). */
    List<PromotionDates> proposableDates();

    /** La cadencia de la promoción, en el texto que escribió Raúl; nulo si no hay promoción o no la tiene. */
    @org.jspecify.annotations.Nullable String cadenceOf(String promotionId);

    /** Color de la marca (#RRGGBB); vacío si la marca no existe. */
    String colorOf(String brandId);

    /** Piezas que puede llevar el kit de la marca (códigos: {@code cita}, {@code cierre}, {@code historia}); vacía si la marca no existe. */
    List<String> kitPiecesOf(String brandId);

    /** Marcas sin color válido (las anteriores a que fuera obligatorio): no generan fondos hasta que se les ponga. */
    List<BrandRef> brandsWithoutColor();

    /** Carpeta con el papel «Material» de la marca (su raíz, sin las excepciones de sus promociones). */
    Optional<String> brandMaterialFolder(String brandId);

    /**
     * Carpetas donde buscar el material (imágenes y vídeos) de una promoción: sus recursos de tipo Drive y la carpeta
     * «Material» en vigor (la suya o la de la marca), sin repetir.
     */
    List<String> materialFolders(String promotionId);

    /** Estilo visual de la promoción («Navidad: rojo, dorado, nieve y estrellas»); vacío si no lo tiene. */
    String visualStyleOf(String promotionId);

    /** Usuario de la marca en las redes («@elde_lito»); vacío si no existe. */
    String handleOf(String brandId);

    /** Falla con 404 si la marca no existe. */
    void requireBrand(String brandId);

    /** Falla con 404 si la promoción no existe. */
    void requirePromotion(String promotionId);

    /** Identidad de la marca (vacía si no se ha rellenado). Solo para el propio módulo: usa tipos de dominio. */
    BrandIdentity identity(String brandId);

    /**
     * Lo que falta en la identidad de la marca para que Claude no proponga a ciegas, en español y con artículo
     * («el tono», «el público»). Vacío si está completa. Los temas que no se tocan vacíos son válidos (no hay temas
     * vetados) y no cuentan como hueco.
     */
    List<String> identityGaps(String brandId);

    /** Identidad de la marca de la promoción, con los nombres que entiende Claude (solo los campos rellenos). */
    Map<String, Object> identityForAi(String promotionId);

    /** Lo que el control de calidad comprueba de la identidad de la marca de la promoción. */
    QualityRules qualityRulesOf(String promotionId);

    /** Reglas de calidad de una marca: expresiones prohibidas, llamada a la acción y hashtags fijos. */
    record QualityRules(List<String> forbiddenExpressions, String callToAction, String fixedHashtags) {}

    /** Decisiones editoriales de la marca, por orden de alta. Solo para el propio módulo: usa tipos de dominio. */
    List<EditorialDecision> decisions(String brandId);

    /** Decisión editorial resumida, para otros módulos. */
    record DecisionRef(long id, String text, java.time.Instant createdAt) {}

    List<DecisionRef> decisionRefs(String brandId);

    /** Lo que Claude necesita saber de una promoción para proponer contenido. */
    java.util.Map<String, Object> summaryForAi(String promotionId);

    /** Fechas de una promoción (la de fin puede faltar: sin límite). */
    record Dates(java.time.LocalDate startsOn, java.time.@org.jspecify.annotations.Nullable LocalDate endsOn) {}

    Dates datesOf(String promotionId);

    /** Promociones de una marca. */
    List<String> promotionIdsOf(String brandId);

    /** Marca de una promoción. */
    String brandOf(String promotionId);

    /** Carpeta con el papel «Encargos» de la marca (sin las excepciones de sus promociones). */
    Optional<String> brandOrdersFolder(String brandId);

    /** Marca resumida, para textos de la interfaz. */
    record BrandRef(String id, String name) {}

    /** Promoción resumida; {@code status}: PLANNED, ACTIVE, PAUSED o FINISHED. */
    record PromotionRef(String id, String brandId, String name, String status, java.time.LocalDate startsOn,
                               java.time.LocalDate endsOn) {
        public boolean active() { return "ACTIVE".equals(status); }

        /** Planificada y que aún no ha terminado: se activará sola el día que empiece. */
        public boolean plannedAhead(java.time.LocalDate today) {
            return "PLANNED".equals(status) && !endsOn.isBefore(today);
        }
    }

    List<BrandRef> brandRefs();

    List<PromotionRef> promotionRefs();

    /** La marca tiene carpeta asignada para todos los papeles (Material, Borradores… Encargos). */
    boolean hasAllBrandFolders(String brandId);

    /** Identificadores de todas las marcas. */
    List<String> brandIds();

    /** Reglas que se aplican a una promoción: las suyas y las comunes. */
    List<String> rulesFor(String promotionId);

    /** Carpeta con el papel «Informes» de la promoción (propia o de la marca). */
    Optional<String> reportsFolder(String promotionId);

    /** Carpetas en vigor por papel («MATERIAL» → ruta…), para contárselas a Claude. */
    java.util.Map<String, String> folderMap(String promotionId);

    /** Carpeta con el papel «Encargos», si la promoción lo tiene. */
    Optional<String> ordersFolder(String promotionId);

    /** Identificadores de todas las promociones. */
    List<String> promotionIds();
}
