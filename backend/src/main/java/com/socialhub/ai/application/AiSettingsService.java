package com.socialhub.ai.application;

import com.socialhub.ai.AiSettings;
import com.socialhub.ai.domain.AiRequestRepository;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.AiSettingStore;
import com.socialhub.ai.domain.ClaudeModel;
import com.socialhub.platform.live.FactoryChanged;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AiSettingsService implements AiSettings {

    static final String DAILY_LIMIT = "daily_limit";

    private final AiSettingStore settings;
    private final AiRequestRepository requests;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public View view() {
        Map<AiRequestType, ClaudeModel> models = new EnumMap<>(AiRequestType.class);
        for (AiRequestType type : AiRequestType.values()) models.put(type, model(type));
        return new View(dailyLimit(), usedToday(), models);
    }

    @Override
    @Transactional
    public View update(int dailyLimit, Map<AiRequestType, ClaudeModel> models) {
        if (dailyLimit < 0 || dailyLimit > MAX_DAILY_LIMIT) {
            throw new IllegalArgumentException("El límite diario debe estar entre 0 y " + MAX_DAILY_LIMIT);
        }
        settings.put(DAILY_LIMIT, String.valueOf(dailyLimit));
        models.forEach((type, model) -> settings.put(key(type), model.name()));
        events.publishEvent(new FactoryChanged("ai", null));
        return view();
    }

    @Override
    @Transactional(readOnly = true)
    public int dailyLimit() {
        return settings.get(DAILY_LIMIT).map(Integer::parseInt).orElse(10);
    }

    @Override
    @Transactional(readOnly = true)
    public ClaudeModel model(AiRequestType type) {
        return settings.get(key(type)).map(ClaudeModel::valueOf).orElse(ClaudeModel.SONNET);
    }

    @Override
    @Transactional(readOnly = true)
    public long usedToday() {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        var pc = Arrays.stream(AiRequestType.values()).filter(AiRequestType::automatic).toList();
        return requests.countSentSinceExcluding(startOfDay, pc);
    }

    private static String key(AiRequestType type) { return "model." + type.name(); }
}
