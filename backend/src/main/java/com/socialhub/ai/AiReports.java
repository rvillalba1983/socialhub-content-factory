package com.socialhub.ai;

import com.socialhub.ai.domain.AiJob;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import tools.jackson.databind.JsonNode;

/** Guarda un informe de Claude (Markdown) en la carpeta de Informes de la promoción, sin sobrescribir nada. */
@PrimaryPort
public interface AiReports {

    /**
     * @param name base del nombre del archivo, por ejemplo «balance-semanal»
     * @return texto de resultado de la tarea
     */
    String save(AiJob job, String name, JsonNode outputs, JsonNode result);
}
