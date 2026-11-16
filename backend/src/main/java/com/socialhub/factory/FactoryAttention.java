package com.socialhub.factory;

import com.socialhub.factory.domain.Attention;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Lo que necesita la atención de Raúl, reunido de todos los módulos: los avisos de «Necesita atención» y las
 * recomendaciones de «Qué hacer ahora». En modo demo no hay recomendaciones ni encargos retenidos.
 */
@PrimaryPort
public interface FactoryAttention {

    Attention attention();
}
