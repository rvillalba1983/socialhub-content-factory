package com.socialhub.promotion.domain;

import com.socialhub.promotion.EditorialDecision;
import java.time.Instant;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface DecisionRepository {

    /** Por orden de alta. */
    List<EditorialDecision> decisions(String brandId);

    EditorialDecision add(String brandId, String text, Instant at);

    /** @return si existía (y era de esa marca) */
    boolean delete(String brandId, long id);
}
