package com.socialhub.publishing.domain;

import java.util.List;

public interface PublicationRepository {

    List<Publication> findByPromotionAndStatus(String promotionId, PublicationStatus status);

    List<Publication> findFlagged(String promotionId);

    void save(Publication publication);
}
