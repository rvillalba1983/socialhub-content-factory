package com.socialhub.pieces;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PieceOrderRepository extends JpaRepository<PieceOrderEntity, Long> {

    Optional<PieceOrderEntity> findFirstBySpecPathAndSpecModifiedAt(String specPath, Instant specModifiedAt);

    List<PieceOrderEntity> findByPromotionId(String promotionId);
}
