package com.socialhub.armory.adapter.out.persistence;

import com.socialhub.armory.domain.ArmoryItem;
import com.socialhub.armory.domain.ArmoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaArmoryRepository implements ArmoryRepository {

    private final ArmoryItemJpaRepository jpa;
    private final ArmoryPersistenceMapper mapper;

    @Override
    public List<ArmoryItem> findAll() {
        return jpa.findAllByOrderByPosition().stream().map(mapper::toDomain).toList();
    }
}
