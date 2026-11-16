package com.socialhub.operations.adapter.in.web;

import com.socialhub.api.ExternalOperationsApi;
import com.socialhub.api.model.ExternalOperationPageDto;
import com.socialhub.api.model.OperationOutcomeDto;
import com.socialhub.operations.ExternalOperationsQueries;
import com.socialhub.operations.domain.ExternalOperation;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ExternalOperationsController implements ExternalOperationsApi {

    private final ExternalOperationsQueries queries;
    private final ExternalOperationWebMapper mapper;

    @Override
    public ResponseEntity<ExternalOperationPageDto> listExternalOperations(
            @Nullable String system, @Nullable OperationOutcomeDto outcome, Integer offset, Integer limit) {
        var page = queries.list(system, outcome == null ? null : ExternalOperation.Outcome.valueOf(outcome.name()),
                offset, limit);
        return ResponseEntity.ok(new ExternalOperationPageDto(
                page.items().stream().map(mapper::toDto).toList(), offset == null ? 0 : offset,
                limit == null ? 100 : limit, page.total()));
    }
}
