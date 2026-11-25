package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublicationDto;
import com.socialhub.api.model.PublicationFileDto;
import com.socialhub.api.model.PublicationInputDto;
import com.socialhub.api.model.PublicationStatusDto;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationStatus;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;

@Mapper
interface PublicationWebMapper {

    DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @org.mapstruct.Mapping(target = "outcome", ignore = true)
    @org.mapstruct.Mapping(target = "duplicates", ignore = true)
    @org.mapstruct.Mapping(target = "moveReason", ignore = true)
    @org.mapstruct.Mapping(target = "sound", expression = "java(toDto(publication.getSound()))")
    PublicationDto toDto(Publication publication);

    default com.socialhub.api.model.PublicationSoundDto toDto(com.socialhub.publishing.domain.PublicationSound sound) {
        var dto = new com.socialhub.api.model.PublicationSoundDto(
                com.socialhub.api.model.SoundModeDto.valueOf(sound.mode().name()), sound.label());
        dto.setTrack(sound.track());
        return dto;
    }

    List<PublicationDto> toDtos(List<Publication> publications);

    @Nullable Platform toDomain(@Nullable PlatformDto platform);

    PublicationStatus toDomain(PublicationStatusDto status);

    /** Los datos de la publicación con lo que se ve además: su resultado, sus duplicados y si puede moverse de hora. */
    default List<PublicationDto> toDtos(List<Publication> publications,
                                        java.util.Map<String, com.socialhub.publishing.PublishingQueries.Insight> insights) {
        return publications.stream().map(p -> {
            PublicationDto dto = toDto(p);
            var insight = insights.get(p.getId());
            if (insight == null) return dto;
            insight.outcome().ifPresent(o -> dto.setOutcome(toDto(o)));
            dto.setDuplicates(insight.duplicates().stream().map(x -> new com.socialhub.api.model.DuplicateRefDto(x.publicationId(),
                    com.socialhub.api.model.DuplicateRefDto.ReasonEnum.valueOf(x.reason().name()))).toList());
            insight.move().ifPresent(m -> dto.setMoveReason(PublicationDto.MoveReasonEnum.valueOf(m.name())));
            return dto;
        }).toList();
    }

    default com.socialhub.api.model.PublicationOutcomeDto toDto(com.socialhub.publishing.domain.PublicationOutcome o) {
        var dto = new com.socialhub.api.model.PublicationOutcomeDto(
                com.socialhub.api.model.PublicationOutcomeDto.ResultEnum.valueOf(o.result().name()), o.result().label());
        if (o.at() != null) dto.setAt(o.at().atOffset(ZoneOffset.UTC));
        if (o.atSource() != null) dto.setAtSource(com.socialhub.api.model.PublicationOutcomeDto.AtSourceEnum.valueOf(o.atSource().name()));
        dto.setRoute(o.route());
        dto.setUrl(o.url());
        dto.setReason(o.reason());
        dto.setAdvice(o.advice());
        return dto;
    }

    default com.socialhub.api.model.SlotProposalDto toDto(com.socialhub.publishing.PublishingQueries.SlotProposal s) {
        var dto = new com.socialhub.api.model.SlotProposalDto(s.publicationId(),
                com.socialhub.api.model.SlotProposalDto.ReasonEnum.valueOf(s.reason().name()), s.currentDay(),
                s.currentTime().format(HH_MM), s.newDay(), s.newTime().format(HH_MM), s.timezone(), s.changesDay(),
                s.afterPromotionEnd(), s.marginMinutes());
        dto.setPromotionEndsOn(s.promotionEndsOn());
        return dto;
    }

    default String toText(LocalTime time) { return time.format(HH_MM); }

    default Publication.Details toDetails(PublicationInputDto input) {
        return new Publication.Details(Platform.valueOf(input.getPlatform().name()), input.getType(), input.getTitle(),
                input.getScheduledOn(), LocalTime.parse(input.getScheduledTime(), HH_MM), input.getTimezone(),
                input.getRoute(), input.getNote(), input.getCaption(), input.getHashtags());
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
        var dto = new PublicationFileDto(file.id(), file.path(), PublicationFileDto.KindEnum.valueOf(file.kind().name()),
                PublicationFileDto.OriginEnum.valueOf(file.origin().name()), file.addedAt().atOffset(ZoneOffset.UTC), available);
        dto.setMimeType(file.mimeType());
        dto.setPage(file.page());
        dto.setSourceName(file.sourceName());
        return dto;
    }
}
