package com.socialhub.pieces.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PieceOrderJpaRepository extends JpaRepository<PieceOrderEntity, Long> {

    Optional<PieceOrderEntity> findFirstBySpecPathAndSpecModifiedAt(String specPath, Instant specModifiedAt);
}
