package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.Agent;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskCheck;
import com.socialhub.production.domain.TaskHistoryEntry;
import com.socialhub.production.domain.TaskStep;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Traducción entre el dominio y las entidades JPA. */
@Mapper
interface ProductionPersistenceMapper {

    @Mapping(target = "history", source = "events")
    Task toDomain(TaskEntity entity);

    @Mapping(target = "events", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(Task task, @MappingTarget TaskEntity entity);

    Station toDomain(StationEntity entity);

    @Mapping(target = "version", ignore = true)
    void update(Station station, @MappingTarget StationEntity entity);

    Agent toDomain(AgentEmbeddable agent);

    AgentEmbeddable toEntity(Agent agent);

    TaskStep toDomain(TaskStepEmbeddable step);

    TaskStepEmbeddable toEntity(TaskStep step);

    TaskCheck toDomain(TaskCheckEmbeddable check);

    TaskCheckEmbeddable toEntity(TaskCheck check);

    TaskHistoryEntry toDomain(TaskEventEntity event);
}
