package com.socialhub.operations.application;

import com.socialhub.operations.ExternalOperationsQueries;
import com.socialhub.operations.domain.ExternalOperation;
import com.socialhub.operations.domain.ExternalOperationRepository;
import com.socialhub.operations.domain.OperationPage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ExternalOperationsService implements ExternalOperationsQueries {

    private final ExternalOperationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public OperationPage list(@Nullable String system, ExternalOperation.@Nullable Outcome outcome,
                              @Nullable Integer offset, @Nullable Integer limit) {
        return repository.find(system == null || system.isBlank() ? null : system, outcome,
                offset == null ? 0 : offset, limit == null ? 100 : limit);
    }
}
