package com.socialhub.ai.domain;

import java.time.LocalDate;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Registro de los encargos semanales ya creados: así no se piden dos veces. */
@SecondaryPort
public interface AiAutoOrderRepository {

    boolean exists(AiAutoOrder.Key key);

    void save(AiAutoOrder order);

    List<AiAutoOrder> ofTypeAndWeek(String type, LocalDate weekStart);
}
