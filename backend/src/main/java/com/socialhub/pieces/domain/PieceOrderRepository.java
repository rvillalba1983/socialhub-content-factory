package com.socialhub.pieces.domain;

import java.time.Instant;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Los specs ya encargados. */
@SecondaryPort
public interface PieceOrderRepository {

    /** El encargo de ese archivo con esa fecha de modificación, si existe. */
    Optional<PieceOrder> findBySpec(String specPath, Instant specModifiedAt);

    void save(PieceOrder order);
}
