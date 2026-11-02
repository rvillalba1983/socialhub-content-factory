package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublicationDto;
import com.socialhub.api.model.PublicationFileDto;
import com.socialhub.api.model.PublicationInputDto;
import com.socialhub.api.model.PublicationStatusDto;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.ZoneOffset;
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

    default Publication.Details toDetails(PublicationInputDto input) {
        return new Publication.Details(Platform.valueOf(input.getPlatform().name()), input.getType(), input.getTitle(),
                input.getScheduledOn(), LocalTime.parse(input.getScheduledTime(), HH_MM), input.getTimezone(),
                input.getRoute(), input.getNote());
    }

    default PublicationStatus initialStatus(PublicationInputDto input) {
        return input.getInitialStatus() == null ? PublicationStatus.IDEA
                : PublicationStatus.valueOf(input.getInitialStatus().name());
    }

    default PublicationFileDto toDto(PublicationFile file) {
        boolean available;
        try {
            available = Files.isRegularFile(Path.of(file.path()));
        } catch (InvalidPathException e) {
            available = false;
        }
        return new PublicationFileDto(file.path(), PublicationFileDto.OriginEnum.valueOf(file.origin().name()),
                file.addedAt().atOffset(ZoneOffset.UTC), available);
    }
}
