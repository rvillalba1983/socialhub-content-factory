package com.socialhub.armory;

import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Altas y cambios en la armería: lo que otros módulos registran (herramientas locales y servicios externos). */
@PrimaryPort
public interface ArmoryRegistry {

    /**
     * Registra una herramienta local con el resultado de su comprobación.
     *
     * @param ok si funciona; si no, queda con error en la armería
     */
    void registerLocalTool(String id, String name, String description, boolean ok, String note, List<String> capabilities);

    /**
     * Registra o actualiza un servicio externo (por ejemplo, Google Drive) con su estado.
     *
     * @param status {@code OK}, {@code BLOCKED} (no disponible) o {@code IDLE} (sin configurar)
     */
    void registerService(String id, String name, String description, String status, String note, List<String> capabilities);
}
