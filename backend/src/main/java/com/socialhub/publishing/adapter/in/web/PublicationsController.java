package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.PublicationsApi;
import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublicationDto;
import com.socialhub.api.model.PublicationFileDto;
import com.socialhub.api.model.PublicationFileInputDto;
import com.socialhub.api.model.PublicationFileListDto;
import com.socialhub.api.model.PublicationInputDto;
import com.socialhub.api.model.PublicationStatusChangeDto;
import com.socialhub.api.model.PublicationPageDto;
import com.socialhub.api.model.PublicationStatusDto;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PublishingService;
import com.socialhub.publishing.QualityService;
import com.socialhub.publishing.domain.QualityIssue;
import com.socialhub.api.model.QualityAcceptanceDto;
import com.socialhub.api.model.QualityIssueDto;
import com.socialhub.api.model.QualityReportDto;
import java.time.ZoneOffset;
import com.socialhub.publishing.domain.PublicationFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PublicationsController implements PublicationsApi {

    private final PublishingQueries queries;
    private final PublishingService service;
    private final QualityService quality;
    private final PublicationWebMapper mapper;

    @Override
    public ResponseEntity<PublicationPageDto> listPublications(String promotionId,
            @Nullable List<PublicationStatusDto> status, @Nullable PlatformDto platform, @Nullable LocalDate from,
            @Nullable LocalDate to, @Nullable Boolean flagged, Integer offset, Integer limit) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("«to» no puede ser anterior a «from»");
        }
        var statuses = status == null ? Set.<com.socialhub.publishing.domain.PublicationStatus>of()
                : status.stream().map(mapper::toDomain).collect(Collectors.toUnmodifiableSet());
        var page = queries.page(new PublicationFilter(promotionId, statuses, mapper.toDomain(platform), from, to, flagged),
                offset, limit);
        return ResponseEntity.ok(new PublicationPageDto(mapper.toDtos(page.items()), offset, limit, page.total()));
    }

    @Override
    public ResponseEntity<PublicationDto> createPublication(String promotionId, String requestGuard,
                                                            PublicationInputDto input) {
        var created = service.create(promotionId, mapper.toDetails(input), mapper.initialStatus(input));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(created));
    }

    @Override
    public ResponseEntity<PublicationDto> updatePublication(String publicationId, String requestGuard,
                                                            PublicationInputDto input) {
        if (input.getInitialStatus() != null) {
            throw new IllegalArgumentException("El estado no se edita aquí: usa el cambio de estado");
        }
        return ResponseEntity.ok(mapper.toDto(service.update(publicationId, mapper.toDetails(input))));
    }

    @Override
    public ResponseEntity<PublicationDto> changePublicationStatus(String publicationId, String requestGuard,
                                                                  PublicationStatusChangeDto change) {
        return ResponseEntity.ok(mapper.toDto(service.changeStatus(publicationId, mapper.toDomain(change.getStatus()))));
    }

    @Override
    public ResponseEntity<PublicationFileListDto> listPublicationFiles(String publicationId) {
        return ResponseEntity.ok(new PublicationFileListDto(queries.files(publicationId).stream().map(mapper::toDto).toList()));
    }

    @Override
    public ResponseEntity<PublicationFileDto> attachPublicationFile(String publicationId, String requestGuard,
                                                                    PublicationFileInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toDto(service.attachManually(publicationId, input.getPath())));
    }

    @Override
    public ResponseEntity<Void> detachPublicationFile(String publicationId, String requestGuard, String path) {
        service.detach(publicationId, path);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<QualityReportDto> getPublicationQuality(String publicationId) {
        return ResponseEntity.ok(report(quality.issues(publicationId)));
    }

    @Override
    public ResponseEntity<QualityReportDto> acceptQualityIssue(String publicationId, String requestGuard,
                                                               QualityAcceptanceDto acceptance) {
        return ResponseEntity.ok(report(quality.accept(publicationId, acceptance.getKey(), acceptance.getReason())));
    }

    private static QualityReportDto report(List<QualityIssue> issues) {
        List<QualityIssueDto> dtos = issues.stream().map(i -> {
            QualityIssueDto dto = new QualityIssueDto(i.key(), QualityIssueDto.SourceEnum.valueOf(i.source().name()),
                    i.message(), !i.open());
            dto.setAcceptedReason(i.acceptedReason());
            dto.setAcceptedAt(i.acceptedAt() == null ? null : i.acceptedAt().atOffset(ZoneOffset.UTC));
            return dto;
        }).toList();
        return new QualityReportDto(dtos, (int) issues.stream().filter(QualityIssue::open).count());
    }
}
