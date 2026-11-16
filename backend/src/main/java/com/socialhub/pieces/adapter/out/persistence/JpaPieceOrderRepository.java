package com.socialhub.pieces.adapter.out.persistence;

import com.socialhub.pieces.domain.PieceOrder;
import com.socialhub.pieces.domain.PieceOrderRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPieceOrderRepository implements PieceOrderRepository {

    private final PieceOrderJpaRepository jpa;

    @Override
    public Optional<PieceOrder> findBySpec(String specPath, Instant specModifiedAt) {
        return jpa.findFirstBySpecPathAndSpecModifiedAt(specPath, specModifiedAt).map(PieceOrderEntity::toDomain);
    }

    @Override
    public void save(PieceOrder order) { jpa.save(new PieceOrderEntity(order)); }
}
