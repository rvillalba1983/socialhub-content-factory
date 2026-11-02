package com.socialhub.armory.adapter.out.persistence;

import com.socialhub.armory.domain.ArmoryItem;
import com.socialhub.armory.domain.ArmoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaArmoryRepository implements ArmoryRepository {

    interface ArmoryItemJpaRepository extends JpaRepository<ArmoryItemEntity, String> {
        List<ArmoryItemEntity> findAllByOrderByPosition();
    }

    @Mapper
    interface ArmoryPersistenceMapper {
        ArmoryItem toDomain(ArmoryItemEntity entity);
    }

    private final ArmoryItemJpaRepository jpa;
    private final ArmoryPersistenceMapper mapper;

    @Override
    public List<ArmoryItem> findAll() {
        return jpa.findAllByOrderByPosition().stream().map(mapper::toDomain).toList();
    }
}
