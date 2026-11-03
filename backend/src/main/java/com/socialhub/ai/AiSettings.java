package com.socialhub.ai;

import com.socialhub.platform.live.FactoryChanged;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Límite diario de encargos a Claude y modelo de cada tipo de encargo. */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class AiSettings {

    static final String DAILY_LIMIT = "daily_limit";
    static final int MAX_DAILY_LIMIT = 100;

    private final AiSettingRepository settings;
    private final AiRequestRepository requests;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record View(int dailyLimit, long usedToday, Map<AiRequestType, ClaudeModel> models) {}

    @Transactional(readOnly = true)
    public View view() {
        Map<AiRequestType, ClaudeModel> models = new EnumMap<>(AiRequestType.class);
        for (AiRequestType type : AiRequestType.values()) models.put(type, model(type));
        return new View(dailyLimit(), usedToday(), models);
    }

    @Transactional
    public View update(int dailyLimit, Map<AiRequestType, ClaudeModel> models) {
        if (dailyLimit < 0 || dailyLimit > MAX_DAILY_LIMIT) {
            throw new IllegalArgumentException("El límite diario debe estar entre 0 y " + MAX_DAILY_LIMIT);
        }
        settings.save(new AiSettingEntity(DAILY_LIMIT, String.valueOf(dailyLimit)));
        models.forEach((type, model) -> settings.save(new AiSettingEntity(key(type), model.name())));
        events.publishEvent(new FactoryChanged("ai", null));
        return view();
    }

    @Transactional(readOnly = true)
    public int dailyLimit() {
        return settings.findById(DAILY_LIMIT).map(s -> Integer.parseInt(s.getValue())).orElse(10);
    }

    @Transactional(readOnly = true)
    public ClaudeModel model(AiRequestType type) {
        return settings.findById(key(type)).map(s -> ClaudeModel.valueOf(s.getValue())).orElse(ClaudeModel.SONNET);
    }

    /** Encargos enviados hoy (día de España) que cuentan en el límite (los de publicación no cuentan). */
    @Transactional(readOnly = true)
    public long usedToday() {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        var pc = java.util.Arrays.stream(AiRequestType.values()).filter(AiRequestType::onPc).map(Enum::name).toList();
        return requests.countBySentAtGreaterThanEqualAndTypeNotIn(startOfDay, pc);
    }

    private static String key(AiRequestType type) { return "model." + type.name(); }
}
