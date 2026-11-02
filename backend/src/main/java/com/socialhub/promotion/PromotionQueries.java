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
