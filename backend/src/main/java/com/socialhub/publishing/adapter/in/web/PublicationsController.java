package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.PublicationsApi;
import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublicationPageDto;
import com.socialhub.api.model.PublicationStatusDto;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.domain.PublicationFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PublicationsController implements PublicationsApi {

    private final PublishingQueries queries;
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
}
