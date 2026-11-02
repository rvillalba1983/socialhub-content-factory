package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublicationDto;
import com.socialhub.api.model.PublicationStatusDto;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;

@Mapper
public interface PublicationWebMapper {

    DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    PublicationDto toDto(Publication publication);

    List<PublicationDto> toDtos(List<Publication> publications);

    @Nullable Platform toDomain(@Nullable PlatformDto platform);

    PublicationStatus toDomain(PublicationStatusDto status);

    default String toText(LocalTime time) { return time.format(HH_MM); }
}
