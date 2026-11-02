package com.socialhub.ai;

import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Aporta contexto común a todos los encargos de una marca (por ejemplo, la memoria editorial). */
@SecondaryPort
public interface AiContextProvider {

    /** @return claves que se añaden a «inputs» del encargo; vacío si no hay nada que añadir */
    Map<String, Object> context(String promotionId, String brandId);
}
