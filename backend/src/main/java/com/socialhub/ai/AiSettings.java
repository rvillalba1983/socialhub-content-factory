package com.socialhub.ai;

import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.ClaudeModel;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Límite diario de encargos a Claude y modelo de cada tipo de encargo. */
@PrimaryPort
public interface AiSettings {

    int MAX_DAILY_LIMIT = 100;

    record View(int dailyLimit, long usedToday, Map<AiRequestType, ClaudeModel> models) {}

    View view();

    View update(int dailyLimit, Map<AiRequestType, ClaudeModel> models);

    int dailyLimit();

    ClaudeModel model(AiRequestType type);

    /** Encargos enviados hoy (día de España) que cuentan en el límite (los de publicación no cuentan). */
    long usedToday();
}
