package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiSettingStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaAiSettingStore implements AiSettingStore {

    private final AiSettingJpaRepository jpa;

    @Override
    public Optional<String> get(String key) { return jpa.findById(key).map(AiSettingEntity::getValue); }

    @Override
    public void put(String key, String value) { jpa.save(new AiSettingEntity(key, value)); }
}
