package com.socialhub.production.adapter.in.web;

import com.socialhub.api.model.AiModelDto;
import com.socialhub.api.model.AvailableCommandDto;
import com.socialhub.api.model.StationCommandTypeDto;
import com.socialhub.api.model.StationDto;
import com.socialhub.api.model.TaskCountsDto;
import com.socialhub.api.model.TaskDto;
import com.socialhub.api.model.TaskEventDto;
import com.socialhub.production.domain.AiModel;
import com.socialhub.production.domain.AvailableCommand;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationCommandType;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskHistoryEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Traducción entre el dominio de producción y los modelos de la API. */
@Mapper
interface ProductionWebMapper {

    @Mapping(target = "events", source = "history")
    @Mapping(target = "availableCommands", expression = "java(toCommandDtos(task.availableCommands()))")
    TaskDto toDto(Task task);

    @Mapping(target = "counts", source = "counts")
    @Mapping(target = "availableCommands", expression = "java(toStationCommandDtos(station.availableCommands()))")
    StationDto toDto(Station station, TaskCountsDto counts);

    TaskEventDto toDto(TaskHistoryEntry entry);

    List<AvailableCommandDto> toCommandDtos(List<AvailableCommand> commands);

    List<StationCommandTypeDto> toStationCommandDtos(List<StationCommandType> commands);

    @Nullable AiModel toDomain(@Nullable AiModelDto model);

    default @Nullable OffsetDateTime toOffsetDateTime(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
