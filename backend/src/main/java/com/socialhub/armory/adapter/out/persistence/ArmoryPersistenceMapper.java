package com.socialhub.armory.adapter.out.persistence;

import com.socialhub.armory.domain.ArmoryItem;
import org.mapstruct.Mapper;

@Mapper
interface ArmoryPersistenceMapper {
    ArmoryItem toDomain(ArmoryItemEntity entity);
}
