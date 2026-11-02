package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.PublicationsApi;
import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublicationDto;
import com.socialhub.api.model.PublicationInputDto;
import com.socialhub.api.model.PublicationStatusChangeDto;
import com.socialhub.api.model.PublicationPageDto;
import com.socialhub.api.model.PublicationStatusDto;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PublishingService;
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
}
