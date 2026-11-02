package com.socialhub.armory.adapter.out.persistence;

import com.socialhub.armory.domain.ArmoryItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface ArmoryPersistenceMapper {
    ArmoryItem toDomain(ArmoryItemEntity entity);

    @Mapping(target = "position", ignore = true)
    void update(ArmoryItem item, @MappingTarget ArmoryItemEntity entity);
}
