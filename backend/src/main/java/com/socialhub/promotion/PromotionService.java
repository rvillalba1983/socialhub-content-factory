package com.socialhub.promotion;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.FolderAssignment;
import com.socialhub.promotion.domain.FolderRepository;
import com.socialhub.promotion.domain.FolderRole;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionDraft;
import com.socialhub.promotion.domain.PromotionRepository;
import com.socialhub.promotion.domain.Slugs;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta y edición de marcas y promociones. Lo creado en modo demo queda marcado como ejemplo. */
@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional
public class PromotionService {

    private final PromotionRepository repository;
    private final AppModeHolder mode;
    private final ApplicationEventPublisher events;
    private final FolderRepository folders;
    private final PromotionQueries queries;

    public Brand createBrand(BrandDraft draft) {
        String id = Slugs.of(draft.name(), repository::brandIdTaken);
        Brand brand = repository.save(new Brand(id, draft.name().strip(), draft.handle().strip(), draft.color(), mode.isDemo()));
        events.publishEvent(new FactoryChanged("brand", id));
        return brand;
    }

    public Brand updateBrand(String id, BrandDraft draft) {
        Brand current = repository.brand(id).orElseThrow(() -> new NotFoundException("No existe la marca " + id));
        Brand brand = repository.save(new Brand(id, draft.name().strip(), draft.handle().strip(), draft.color(), current.demo()));
        events.publishEvent(new FactoryChanged("brand", id));
        return brand;
    }

    public Promotion createPromotion(PromotionDraft draft) {
        validate(draft);
        String id = Slugs.of(draft.name(), repository::promotionIdTaken);
        Promotion promotion = repository.save(draft.toPromotion(id, mode.isDemo()));
        events.publishEvent(new FactoryChanged("promotion", id));
        return promotion;
    }

    public Promotion updatePromotion(String id, PromotionDraft draft) {
        Promotion current = repository.promotion(id).orElseThrow(() -> new NotFoundException("No existe la promoción " + id));
        validate(draft);
        Promotion promotion = repository.save(draft.toPromotion(id, current.demo()));
        events.publishEvent(new FactoryChanged("promotion", id));
        return promotion;
    }

    private void validate(PromotionDraft draft) {
        if (repository.brand(draft.brandId()).isEmpty()) {
            throw new IllegalArgumentException("No existe la marca " + draft.brandId());
        }
        if (draft.endsOn().isBefore(draft.startsOn())) {
            throw new IllegalArgumentException("La promoción no puede terminar antes de empezar");
        }
    }

    /** Sustituye los papeles de carpeta de la marca. Las promociones los heredan. */
    public List<FolderAssignment> replaceBrandFolders(String brandId, Map<FolderRole, String> assignments) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        folders.replaceBrandFolders(brandId, clean(assignments));
        events.publishEvent(new FactoryChanged("brand", brandId));
        return queries.brandFolders(brandId);
    }

    /** Sustituye las excepciones de la promoción. Devuelve los papeles que quedan en vigor. */
    public List<FolderAssignment> replacePromotionFolders(String promotionId, Map<FolderRole, String> assignments) {
        if (repository.promotion(promotionId).isEmpty()) {
            throw new NotFoundException("No existe la promoción " + promotionId);
        }
        folders.replacePromotionFolders(promotionId, clean(assignments));
        events.publishEvent(new FactoryChanged("promotion", promotionId));
        return queries.folders(promotionId);
    }

    /** Rutas completas, sin espacios sobrantes. No se exige que existan: G: puede no estar montada ahora. */
    private static Map<FolderRole, String> clean(Map<FolderRole, String> assignments) {
        Map<FolderRole, String> clean = new EnumMap<>(FolderRole.class);
        assignments.forEach((role, location) -> {
            String value = location == null ? "" : location.strip();
            if (value.isEmpty()) return;
            try {
                if (!Path.of(value).isAbsolute()) {
                    throw new IllegalArgumentException("La carpeta de «" + role.label() + "» debe ser una ruta completa: " + value);
                }
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("Ruta no válida para «" + role.label() + "»: " + value);
            }
            clean.put(role, value);
        });
        return clean;
    }
}
