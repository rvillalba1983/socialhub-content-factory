package com.socialhub.armory.application;

import com.socialhub.armory.ArmoryRegistry;
import com.socialhub.armory.domain.ArmoryItem;
import com.socialhub.armory.domain.ArmoryRepository;
import com.socialhub.platform.live.FactoryChanged;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Altas y cambios en la armería. */
@Service
@RequiredArgsConstructor
class ArmoryRegistryService implements ArmoryRegistry {

    private final ArmoryRepository repository;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public void registerLocalTool(String id, String name, String description, boolean ok, String note,
                                  List<String> capabilities) {
        repository.save(new ArmoryItem(id, "TOOL", name, description, ok ? "OK" : "FAILED", note, capabilities, false));
        events.publishEvent(new FactoryChanged("armory", id));
    }

    @Override
    @Transactional
    public void registerService(String id, String name, String description, String status, String note,
                                List<String> capabilities) {
        repository.save(new ArmoryItem(id, "SERVICE", name, description, status, note, capabilities, false));
        events.publishEvent(new FactoryChanged("armory", id));
    }
}
