package com.socialhub.factory;

import com.socialhub.factory.domain.Attention;
import com.socialhub.production.ProductionAlert;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Lo que necesita la atención de Raúl, reunido de todos los módulos: los avisos de «Necesita atención» y las
 * recomendaciones de «Qué hacer ahora». En modo demo no hay recomendaciones ni encargos retenidos.
 */
@PrimaryPort
public interface FactoryAttention {

    /** @param productionAlerts avisos de producción ya leídos (los da {@code ProductionQueries.alertsOf}) */
    Attention attention(List<ProductionAlert> productionAlerts);
}
