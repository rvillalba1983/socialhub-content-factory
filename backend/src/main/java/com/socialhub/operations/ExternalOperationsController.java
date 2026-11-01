package com.socialhub.operations;

import com.socialhub.api.ExternalOperationsApi;
import com.socialhub.api.model.ExternalOperationDto;
import com.socialhub.api.model.ExternalOperationPageDto;
import com.socialhub.api.model.OperationOutcomeDto;
import com.socialhub.platform.OffsetLimit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ExternalOperationsController implements ExternalOperationsApi {

    private final ExternalOperationRepository repository;
    private final ExternalOperationWebMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ExternalOperationPageDto> listExternalOperations(
            @Nullable String system, @Nullable OperationOutcomeDto outcome, Integer offset, Integer limit) {
        Specification<ExternalOperation> spec = Specification.unrestricted();
        if (system != null && !system.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("system"), system));
        }
        if (outcome != null) {
            var value = ExternalOperation.Outcome.valueOf(outcome.name());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("outcome"), value));
        }
        var page = OffsetLimit.of(offset, limit, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        Page<ExternalOperation> result = repository.findAll(spec, page);
        return ResponseEntity.ok(new ExternalOperationPageDto(
                result.map(mapper::toDto).getContent(), (int) page.offset(), page.limit(), result.getTotalElements()));
    }
}
