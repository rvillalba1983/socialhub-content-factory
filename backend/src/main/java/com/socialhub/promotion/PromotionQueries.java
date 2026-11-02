package com.socialhub.promotion;

import com.socialhub.platform.NotFoundException;
import com.socialhub.promotion.domain.Brand;
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
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionQueries {

    private final PromotionRepository repository;
    private final FolderRepository folderRepository;
    private final com.socialhub.promotion.domain.IdentityRepository identities;
    private final com.socialhub.promotion.domain.DecisionRepository decisions;

    public List<Brand> brands() { return repository.brands(); }

    public List<Promotion> promotions() { return repository.promotions(); }

    public Promotion promotion(String id) {
        return repository.promotion(id).orElseThrow(() -> new NotFoundException("No existe la promoción " + id));
    }

    public List<String> commonRules() { return repository.commonRules(); }

    /**
     * Carpetas donde las piezas pueden leer y escribir: las de «Recursos» de tipo Carpeta y las que tienen un papel,
     * sin repetir y en ese orden.
     */
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
    public List<FolderAssignment> brandFolders(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        return folderRepository.brandFolders(brandId).entrySet().stream()
                .map(e -> new FolderAssignment(e.getKey(), e.getValue(), Origin.BRAND))
                .toList();
    }

    /** Papeles en vigor de una promoción: los de la marca, salvo los que la promoción cambia. */
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
    public List<String> allFolders() {
        var all = new LinkedHashSet<String>();
        brands().forEach(b -> folderRepository.brandFolders(b.id()).values().forEach(all::add));
        promotionIds().forEach(id -> all.addAll(driveFolders(id)));
        return List.copyOf(all);
    }

    /** Promociones activas cuyas fechas se cruzan con ese intervalo (incluido). */
    public List<String> activeBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return repository.promotions().stream()
                .filter(p -> "ACTIVE".equals(p.status()) && !p.startsOn().isAfter(to) && !p.endsOn().isBefore(from))
                .map(Promotion::id)
                .toList();
    }

    /** Identidad de la marca (vacía si no se ha rellenado). */
    public BrandIdentity identity(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        return identities.identity(brandId).orElse(BrandIdentity.EMPTY);
    }

    /** Identidad que se aplica a una promoción: la de su marca. */
    public BrandIdentity identityOf(String promotionId) { return identity(brandOf(promotionId)); }

    /** Decisiones editoriales de la marca, por orden de alta. */
    public List<EditorialDecision> decisions(String brandId) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        return decisions.decisions(brandId);
    }

    /** Marca de una promoción. */
    public String brandOf(String promotionId) { return promotion(promotionId).brandId(); }

    /** Carpeta con el papel «Encargos» de la marca (sin las excepciones de sus promociones). */
    public Optional<String> brandOrdersFolder(String brandId) {
        return Optional.ofNullable(folderRepository.brandFolders(brandId).get(FolderRole.ORDERS));
    }

    /** Identificadores de todas las marcas. */
    public List<String> brandIds() { return brands().stream().map(Brand::id).toList(); }

    /** Reglas que se aplican a una promoción: las suyas y las comunes. */
    public List<String> rulesFor(String promotionId) {
        var all = new ArrayList<String>(promotion(promotionId).rules());
        all.addAll(commonRules());
        return List.copyOf(all);
    }

    /** Carpeta con el papel «Informes» de la promoción (propia o de la marca). */
    public Optional<String> reportsFolder(String promotionId) { return folderOf(promotionId, FolderRole.REPORTS); }

    /** Carpetas en vigor por papel («MATERIAL» → ruta…), para contárselas a Claude. */
    public java.util.Map<String, String> folderMap(String promotionId) {
        var map = new java.util.LinkedHashMap<String, String>();
        folders(promotionId).forEach(f -> map.put(f.role().name(), f.location()));
        return map;
    }

    private Optional<String> folderOf(String promotionId, FolderRole role) {
        return folders(promotionId).stream().filter(f -> f.role() == role).map(FolderAssignment::location).findFirst();
    }

    /** Carpeta con el papel «Encargos», si la promoción lo tiene. */
    public Optional<String> ordersFolder(String promotionId) {
        return folders(promotionId).stream()
                .filter(f -> f.role() == FolderRole.ORDERS)
                .map(FolderAssignment::location)
                .findFirst();
    }

    /** Identificadores de todas las promociones. */
    public List<String> promotionIds() {
        return repository.promotions().stream().map(Promotion::id).toList();
    }
}
