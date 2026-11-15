package com.socialhub.operations.adapter.out.persistence;

import com.socialhub.operations.domain.ExternalOperation;
import com.socialhub.operations.domain.ExternalOperationRepository;
import com.socialhub.operations.domain.OperationPage;
import com.socialhub.platform.OffsetLimit;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaExternalOperationRepository implements ExternalOperationRepository {

    private final ExternalOperationJpaRepository jpa;

    @Override
    public void saveAll(List<ExternalOperation> operations) {
        jpa.saveAll(operations.stream().map(ExternalOperationEntity::of).toList());
    }

    @Override
    public OperationPage find(@Nullable String system, ExternalOperation.@Nullable Outcome outcome, int offset, int limit) {
        Specification<ExternalOperationEntity> spec = Specification.unrestricted();
        if (system != null && !system.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("system"), system));
        }
        if (outcome != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("outcome"), outcome));
        }
        var page = OffsetLimit.of(offset, limit, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        Page<ExternalOperationEntity> result = jpa.findAll(spec, page);
        return new OperationPage(result.map(ExternalOperationEntity::toDomain).getContent(), result.getTotalElements());
    }

    @Override
    public int deleteOlderThan(Instant limit) { return jpa.deleteOlderThan(limit); }
}
