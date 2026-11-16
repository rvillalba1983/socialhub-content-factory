package com.socialhub.promotion.application;

import com.socialhub.platform.NotFoundException;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandIdentity;
import com.socialhub.promotion.domain.EditorialDecision;
import com.socialhub.promotion.domain.FolderAssignment;
import com.socialhub.promotion.domain.FolderAssignment.Origin;
import com.socialhub.promotion.domain.FolderRepository;
import com.socialhub.promotion.domain.FolderRole;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PromotionQueriesService implements PromotionQueries {

    private final PromotionRepository repository;
    private final FolderRepository folderRepository;
    private final com.socialhub.promotion.domain.IdentityRepository identities;
    private final com.socialhub.promotion.domain.DecisionRepository decisions;

    @Override
    public List<Brand> brands() { return repository.brands(); }

    @Override
    public List<Promotion> promotions() { return repository.promotions(); }

    @Override
    public Promotion promotion(String id) {
        return repository.promotion(id).orElseThrow(() -> new NotFoundException("No existe la promoción " + id));
    }

    @Override
    public List<String> commonRules() { return repository.commonRules(); }

    /**
     * Carpetas donde las piezas pueden leer y escribir: las de «Recursos» de tipo Carpeta y las que tienen un papel,
     * sin repetir y en ese orden.
     */
    @Override
    public List<String> driveFolders(String promotionId) {
        var all = new LinkedHashSet<String>();
        promotion(promotionId).resources().stream()
                .filter(r -> "DRIVE".equals(r.kind()))
                .map(Promotion.Resource::location)
                .forEach(all::add);
        folders(promotionId).forEach(f -> all.add(f.location()));
        return List.copyOf(all);
    }

    /** Papeles de carpeta de la marca. */
    @Override
    public List<FolderAssignment> brandFolders(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        return folderRepository.brandFolders(brandId).entrySet().stream()
                .map(e -> new FolderAssignment(e.getKey(), e.getValue(), Origin.BRAND))
                .toList();
    }

    /** Papeles en vigor de una promoción: los de la marca, salvo los que la promoción cambia. */
    @Override
    public List<FolderAssignment> folders(String promotionId) {
        Promotion promotion = promotion(promotionId);
        Map<FolderRole, String> brand = folderRepository.brandFolders(promotion.brandId());
        Map<FolderRole, String> own = folderRepository.promotionFolders(promotionId);
        List<FolderAssignment> result = new ArrayList<>();
        for (FolderRole role : FolderRole.values()) {
            if (own.containsKey(role)) result.add(new FolderAssignment(role, own.get(role), Origin.PROMOTION));
            else if (brand.containsKey(role)) result.add(new FolderAssignment(role, brand.get(role), Origin.BRAND));
        }
        return result;
    }

    /** Todas las carpetas configuradas: las de las marcas y las de cada promoción, sin repetir. */
    @Override
    public List<String> allFolders() {
        var all = new LinkedHashSet<String>();
        brands().forEach(b -> folderRepository.brandFolders(b.id()).values().forEach(all::add));
        promotionIds().forEach(id -> all.addAll(driveFolders(id)));
        return List.copyOf(all);
    }

    /** Promociones activas cuyas fechas se cruzan con ese intervalo (incluido). */
    @Override
    public List<String> activeBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return repository.promotions().stream()
                .filter(p -> "ACTIVE".equals(p.status()) && !p.startsOn().isAfter(to) && !p.endsOn().isBefore(from))
                .map(Promotion::id)
                .toList();
    }

    /**
     * Promociones que reciben propuestas (activas o planificadas: una planificada se activa sola el día que empieza)
     * cuyas fechas se cruzan con ese intervalo (incluido). Las pausadas y las terminadas no reciben nada.
     */
    @Override
    public List<String> proposableBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return repository.promotions().stream()
                .filter(p -> ("ACTIVE".equals(p.status()) || "PLANNED".equals(p.status()))
                        && !p.startsOn().isAfter(to) && !p.endsOn().isBefore(from))
                .map(Promotion::id)
                .toList();
    }

    

    /** Promociones activas o planificadas, con sus fechas (las que reciben propuestas). */
    @Override
    public List<PromotionDates> proposableDates() {
        return repository.promotions().stream()
                .filter(p -> "ACTIVE".equals(p.status()) || "PLANNED".equals(p.status()))
                .map(p -> new PromotionDates(p.id(), p.startsOn(), p.endsOn()))
                .toList();
    }

    /** La cadencia de la promoción, en el texto que escribió Raúl; nulo si no hay promoción o no la tiene. */
    @Override
    public @org.jspecify.annotations.Nullable String cadenceOf(String promotionId) {
        return repository.promotion(promotionId).map(Promotion::cadence).orElse(null);
    }

    /** Usuario de la marca en las redes («@elde_lito»); vacío si no existe. */
    @Override
    public String handleOf(String brandId) {
        return repository.brand(brandId).map(b -> b.handle()).orElse("");
    }

    /** Falla con 404 si la marca no existe. */
    @Override
    public void requireBrand(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
    }

    /** Falla con 404 si la promoción no existe. */
    @Override
    public void requirePromotion(String promotionId) {
        if (repository.promotion(promotionId).isEmpty()) throw new NotFoundException("No existe la promoción " + promotionId);
    }

    /** Identidad de la marca (vacía si no se ha rellenado). Solo para el propio módulo: usa tipos de dominio. */
    @Override
    public BrandIdentity identity(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        return identities.identity(brandId).orElse(BrandIdentity.EMPTY);
    }

    /**
     * Lo que falta en la identidad de la marca para que Claude no proponga a ciegas, en español y con artículo
     * («el tono», «el público»). Vacío si está completa. Los temas que no se tocan vacíos son válidos (no hay temas
     * vetados) y no cuentan como hueco.
     */
    @Override
    public List<String> identityGaps(String brandId) {
        BrandIdentity identity = identity(brandId);
        List<String> gaps = new ArrayList<>();
        if (identity.tone().isBlank()) gaps.add("el tono");
        if (identity.audience().isBlank()) gaps.add("el público");
        return gaps;
    }

    /** Identidad de la marca de la promoción, con los nombres que entiende Claude (solo los campos rellenos). */
    @Override
    public Map<String, Object> identityForAi(String promotionId) { return identity(brandOf(promotionId)).forAi(); }

    /** Lo que el control de calidad comprueba de la identidad de la marca de la promoción. */
    @Override
    public QualityRules qualityRulesOf(String promotionId) {
        BrandIdentity identity = identity(brandOf(promotionId));
        return new QualityRules(identity.forbiddenExpressions(), identity.callToAction(), identity.fixedHashtags());
    }

    

    /** Decisiones editoriales de la marca, por orden de alta. Solo para el propio módulo: usa tipos de dominio. */
    @Override
    public List<EditorialDecision> decisions(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        return decisions.decisions(brandId);
    }

    

    @Override
    public List<DecisionRef> decisionRefs(String brandId) {
        return decisions(brandId).stream().map(d -> new DecisionRef(d.id(), d.text(), d.createdAt())).toList();
    }

    /** Lo que Claude necesita saber de una promoción para proponer contenido. */
    @Override
    public java.util.Map<String, Object> summaryForAi(String promotionId) {
        Promotion p = promotion(promotionId);
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("name", p.name());
        if (!p.description().isBlank()) m.put("description", p.description());
        if (!p.scope().isBlank()) m.put("scope", p.scope());
        if (!p.cadence().isBlank()) m.put("cadence", p.cadence());
        m.put("dates", p.startsOn() + " – " + p.endsOn());
        if (!p.goals().isEmpty()) m.put("goals", p.goals());
        if (!p.milestones().isEmpty()) {
            m.put("milestones", p.milestones().stream().map(x -> x.startsOn() + (x.endsOn() == null ? "" : " – " + x.endsOn())
                    + ": " + x.label()).toList());
        }
        return m;
    }

    

    @Override
    public Dates datesOf(String promotionId) {
        Promotion p = promotion(promotionId);
        return new Dates(p.startsOn(), p.endsOn());
    }

    /** Promociones de una marca. */
    @Override
    public List<String> promotionIdsOf(String brandId) {
        return repository.promotions().stream().filter(p -> p.brandId().equals(brandId)).map(Promotion::id).toList();
    }

    /** Marca de una promoción. */
    @Override
    public String brandOf(String promotionId) { return promotion(promotionId).brandId(); }

    /** Carpeta con el papel «Encargos» de la marca (sin las excepciones de sus promociones). */
    @Override
    public Optional<String> brandOrdersFolder(String brandId) {
        return Optional.ofNullable(folderRepository.brandFolders(brandId).get(FolderRole.ORDERS));
    }

    

    

    @Override
    public List<BrandRef> brandRefs() { return brands().stream().map(b -> new BrandRef(b.id(), b.name())).toList(); }

    @Override
    public List<PromotionRef> promotionRefs() {
        return promotions().stream()
                .map(p -> new PromotionRef(p.id(), p.brandId(), p.name(), p.status(), p.startsOn(), p.endsOn()))
                .toList();
    }

    /** La marca tiene carpeta asignada para todos los papeles (Material, Borradores… Encargos). */
    @Override
    public boolean hasAllBrandFolders(String brandId) {
        return folderRepository.brandFolders(brandId).keySet().containsAll(java.util.List.of(FolderRole.values()));
    }

    /** Identificadores de todas las marcas. */
    @Override
    public List<String> brandIds() { return brands().stream().map(Brand::id).toList(); }

    /** Reglas que se aplican a una promoción: las suyas y las comunes. */
    @Override
    public List<String> rulesFor(String promotionId) {
        var all = new ArrayList<String>(promotion(promotionId).rules());
        all.addAll(commonRules());
        return List.copyOf(all);
    }

    /** Carpeta con el papel «Informes» de la promoción (propia o de la marca). */
    @Override
    public Optional<String> reportsFolder(String promotionId) { return folderOf(promotionId, FolderRole.REPORTS); }

    /** Carpetas en vigor por papel («MATERIAL» → ruta…), para contárselas a Claude. */
    @Override
    public java.util.Map<String, String> folderMap(String promotionId) {
        var map = new java.util.LinkedHashMap<String, String>();
        folders(promotionId).forEach(f -> map.put(f.role().name(), f.location()));
        return map;
    }

    private Optional<String> folderOf(String promotionId, FolderRole role) {
        return folders(promotionId).stream().filter(f -> f.role() == role).map(FolderAssignment::location).findFirst();
    }

    /** Carpeta con el papel «Encargos», si la promoción lo tiene. */
    @Override
    public Optional<String> ordersFolder(String promotionId) {
        return folders(promotionId).stream()
                .filter(f -> f.role() == FolderRole.ORDERS)
                .map(FolderAssignment::location)
                .findFirst();
    }

    /** Identificadores de todas las promociones. */
    @Override
    public List<String> promotionIds() {
        return repository.promotions().stream().map(Promotion::id).toList();
    }
}
