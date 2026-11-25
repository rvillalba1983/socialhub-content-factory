package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationSound;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface PublicationPersistenceMapper {

    @Mapping(target = "sound", expression = "java(sound(entity))")
    Publication toDomain(PublicationEntity entity);

    @Mapping(target = "version", ignore = true)
    @Mapping(target = "soundMode", source = "sound.mode")
    @Mapping(target = "soundTrack", source = "sound.track")
    void update(Publication publication, @MappingTarget PublicationEntity entity);

    default PublicationSound sound(PublicationEntity entity) {
        return entity.getSoundMode() == null ? PublicationSound.NONE : new PublicationSound(entity.getSoundMode(), entity.getSoundTrack());
    }
}
