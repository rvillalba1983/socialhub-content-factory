package com.socialhub.promotion;

import com.socialhub.platform.NotFoundException;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionRepository;
import java.util.List;
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

    public List<Brand> brands() { return repository.brands(); }

    public List<Promotion> promotions() { return repository.promotions(); }

    public Promotion promotion(String id) {
        return repository.promotion(id).orElseThrow(() -> new NotFoundException("No existe la promoción " + id));
    }

    public List<String> commonRules() { return repository.commonRules(); }
}
