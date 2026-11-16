package com.socialhub.publishing;

import com.socialhub.promotion.PromotionQueries.DecisionRef;
import com.socialhub.publishing.domain.EditorialMemoryRepository;
import com.socialhub.publishing.domain.Publication;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Lo que Raúl ya ha decidido y rechazado de una marca, para que Claude no repita. */
@PrimaryPort
public interface EditorialMemory {

    record Snapshot(List<DecisionRef> decisions, List<EditorialMemoryRepository.Rejection> rejections,
                           List<EditorialMemoryRepository.Correction> corrections, List<Publication> published) {}

    Snapshot snapshot(String brandId);

    Map<String, Object> context(String promotionId, String brandId);
}
