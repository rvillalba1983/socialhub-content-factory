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
    private final com.socialhub.publishing.EditorialMemory memory;
    private final PublicationWebMapper mapper;
    private final com.socialhub.publishing.ExternalCalendar externalCalendar;

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
        var reason = change.getReason() == null ? null
                : com.socialhub.publishing.domain.RejectionReason.valueOf(change.getReason().name());
        return ResponseEntity.ok(mapper.toDto(service.changeStatus(publicationId, mapper.toDomain(change.getStatus()),
                reason, change.getNote())));
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
    public ResponseEntity<com.socialhub.api.model.PublishJobDto> getPublishJob(String publicationId) {
        var job = queries.publishJob(publicationId)
                .orElseThrow(() -> new com.socialhub.platform.NotFoundException("Esta publicación no se ha programado todavía"));
        var routes = queries.routes().stream()
                .map(r -> new com.socialhub.api.model.PublishRouteStateDto(
                        com.socialhub.api.model.PublishRouteStateDto.IdEnum.valueOf(r.id().name()), r.label(), r.configured()))
                .toList();
        var dto = new com.socialhub.api.model.PublishJobDto(job.getPublicationId(),
                com.socialhub.api.model.PublishJobDto.StatusEnum.valueOf(job.getStatus().name()), job.getStatus().label(),
                routes, job.getAttempts(), job.getLog());
        dto.setRoute(job.getRoute());
        dto.setTaskId(job.getTaskId());
        dto.setExternalId(job.getExternalId());
        dto.setUrl(job.getUrl());
        if (job.getNextTryAt() != null) dto.setNextTryAt(job.getNextTryAt().atOffset(ZoneOffset.UTC));
        dto.setLastError(job.getLastError());
        dto.setManualFile(job.getManualFile());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<com.socialhub.api.model.ExternalCalendarDto> getExternalCalendar(String promotionId,
            LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(62).isBefore(to)) {
            throw new IllegalArgumentException("Rango de fechas no válido (máximo dos meses)");
        }
        var view = externalCalendar.view(promotionId, from, to);
        var items = view.items().stream().map(i -> {
            var dto = new com.socialhub.api.model.ExternalPostDto(
                    com.socialhub.api.model.ExternalSourceIdDto.valueOf(i.source().name()), i.source().label(),
                    PlatformDto.valueOf(i.platform().name()), i.at().atOffset(ZoneOffset.UTC),
                    com.socialhub.api.model.ExternalPostDto.StatusEnum.valueOf(i.status().name()));
            dto.setText(i.text());
            dto.setUrl(i.url());
            dto.setExternalId(i.externalId());
            dto.setPublicationId(i.publicationId());
            return dto;
        }).toList();
        var sources = view.states().stream().map(st -> {
            var dto = new com.socialhub.api.model.ExternalSourceDto(
                    com.socialhub.api.model.ExternalSourceIdDto.valueOf(st.source().name()), st.source().label());
            if (st.syncedAt() != null) dto.setSyncedAt(st.syncedAt().atOffset(ZoneOffset.UTC));
            dto.setError(st.error());
            return dto;
        }).toList();
        return ResponseEntity.ok(new com.socialhub.api.model.ExternalCalendarDto(items, sources));
    }

    @Override
    public ResponseEntity<com.socialhub.api.model.PublicationMetricsDto> getPublicationMetrics(String publicationId) {
        var m = queries.metrics(publicationId)
                .orElseThrow(() -> new com.socialhub.platform.NotFoundException("Aún no se han pedido sus métricas"));
        var dto = new com.socialhub.api.model.PublicationMetricsDto(m.publicationId(),
                com.socialhub.api.model.PublicationMetricsDto.StatusEnum.valueOf(m.status().name()),
                m.requestedAt().atOffset(ZoneOffset.UTC), m.attempts());
        if (m.measuredAt() != null) dto.setMeasuredAt(m.measuredAt().atOffset(ZoneOffset.UTC));
        dto.setViews(m.views());
        dto.setReach(m.reach());
        dto.setLikes(m.likes());
        dto.setComments(m.comments());
        dto.setSaves(m.saves());
        dto.setShares(m.shares());
        dto.setNote(m.note());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<com.socialhub.api.model.PromotionMetricsDto> getPromotionMetrics(String promotionId) {
        var report = queries.promotionMetrics(promotionId);
        var networks = report.byNetwork().stream().map(n -> {
            var dto = new com.socialhub.api.model.NetworkMetricsDto(
                    com.socialhub.api.model.NetworkMetricsDto.NetworkEnum.valueOf(n.platform() == null ? "TOTAL" : n.platform().name()),
                    n.publications());
            dto.setViews(n.sums().views());
            dto.setReach(n.sums().reach());
            dto.setLikes(n.sums().likes());
            dto.setComments(n.sums().comments());
            dto.setSaves(n.sums().saves());
            dto.setShares(n.sums().shares());
            return dto;
        }).toList();
        var formats = report.byFormat().stream().map(f -> {
            var dto = new com.socialhub.api.model.FormatMetricsDto(f.type(),
                    com.socialhub.api.model.FormatMetricsDto.NetworkEnum.valueOf(f.platform().name()), f.publications(), f.lowSample());
            dto.setViews(f.averages().views());
            dto.setReach(f.averages().reach());
            dto.setLikes(f.averages().likes());
            dto.setComments(f.averages().comments());
            dto.setSaves(f.averages().saves());
            dto.setShares(f.averages().shares());
            return dto;
        }).toList();
        return ResponseEntity.ok(new com.socialhub.api.model.PromotionMetricsDto(report.measured(), report.pending(), networks, formats));
    }

    @Override
    public ResponseEntity<Void> clearPublicationFlag(String publicationId, String requestGuard) {
        service.clearFlag(publicationId);
        return ResponseEntity.noContent().build();
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

    @Override
    public ResponseEntity<PublicationDto> rejectPublicationText(String publicationId, String requestGuard,
                                                                com.socialhub.api.model.TextRejectionDto rejection) {
        return ResponseEntity.ok(mapper.toDto(service.discardTexts(publicationId,
                com.socialhub.publishing.domain.RejectionReason.valueOf(rejection.getReason().name()), rejection.getNote())));
    }

    @Override
    public ResponseEntity<com.socialhub.api.model.EditorialMemoryDto> getEditorialMemory(String brandId) {
        var s = memory.snapshot(brandId);
        var decisions = s.decisions().stream().map(d -> new com.socialhub.api.model.EditorialDecisionDto(d.id(), d.text(),
                d.createdAt().atOffset(ZoneOffset.UTC))).toList();
        var rejections = s.rejections().stream().map(r -> {
            var dto = new com.socialhub.api.model.EditorialMemoryRejectionsInnerDto(
                    com.socialhub.api.model.EditorialMemoryRejectionsInnerDto.KindEnum.valueOf(r.kind().name()),
                    com.socialhub.api.model.RejectionReasonDto.valueOf(r.reason().name()), r.title(), r.at().atOffset(ZoneOffset.UTC));
            dto.setNote(r.note());
            dto.setContent(r.content());
            dto.setPublicationId(r.publicationId());
            return dto;
        }).toList();
        var corrections = s.corrections().stream().map(c -> {
            var dto = new com.socialhub.api.model.EditorialMemoryCorrectionsInnerDto(c.at().atOffset(ZoneOffset.UTC));
            dto.setPublicationId(c.publicationId());
            dto.setBeforeCaption(c.beforeCaption());
            dto.setAfterCaption(c.afterCaption());
            dto.setBeforeHashtags(c.beforeHashtags());
            dto.setAfterHashtags(c.afterHashtags());
            return dto;
        }).toList();
        return ResponseEntity.ok(new com.socialhub.api.model.EditorialMemoryDto(decisions, rejections, corrections,
                mapper.toDtos(s.published())));
    }
}
