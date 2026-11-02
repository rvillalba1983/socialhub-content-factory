package com.socialhub.promotion;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionDraft;
import com.socialhub.promotion.domain.PromotionRepository;
import com.socialhub.promotion.domain.Slugs;
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
}
