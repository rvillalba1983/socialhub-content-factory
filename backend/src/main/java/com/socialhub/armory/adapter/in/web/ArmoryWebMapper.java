package com.socialhub.armory.adapter.in.web;

import com.socialhub.api.model.ArmoryItemDto;
import com.socialhub.armory.domain.ArmoryItem;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper
public interface ArmoryWebMapper {
    List<ArmoryItemDto> toDtos(List<ArmoryItem> items);

    ArmoryItemDto toDto(ArmoryItem item);
}
