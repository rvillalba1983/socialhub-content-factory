package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Publication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface PublicationPersistenceMapper {

    Publication toDomain(PublicationEntity entity);

    @Mapping(target = "version", ignore = true)
    void update(Publication publication, @MappingTarget PublicationEntity entity);
}
