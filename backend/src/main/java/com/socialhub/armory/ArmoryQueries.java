package com.socialhub.armory;

import com.socialhub.armory.domain.ArmoryItem;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Consultas de la armería (solo lectura). */
@PrimaryPort
public interface ArmoryQueries {

    List<ArmoryItem> items();

    /** Un servicio real (no de ejemplo) que no está disponible, por ejemplo Google Drive sin montar. */
    record BlockedService(String id, String name, String note) {}

    /** Los servicios reales bloqueados: salen en «Necesita atención». */
    List<BlockedService> blockedServices();
}
