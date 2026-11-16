package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiRequest;
import com.socialhub.ai.domain.AiRequestPage;
import com.socialhub.ai.domain.AiRequestRepository;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.platform.OffsetLimit;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaAiRequestRepository implements AiRequestRepository {

    private final AiRequestJpaRepository jpa;

    @Override
    public void save(AiRequest request) { jpa.save(AiRequestEntity.of(request)); }

    @Override
    public long countSentSince(Instant since) { return jpa.countBySentAtGreaterThanEqual(since); }

    @Override
    public long countSentSinceExcluding(Instant since, Collection<AiRequestType> excluded) {
        return jpa.countBySentAtGreaterThanEqualAndTypeNotIn(since, names(excluded));
    }

    @Override
    public Optional<AiRequest> latestWaitingOf(String taskId) {
        return jpa.findFirstByTaskIdAndStatusOrderBySentAtDesc(taskId, AiRequest.Status.SENT)
                .map(AiRequestEntity::toDomain);
    }

    @Override
    public List<AiRequest> ofTask(String taskId) {
        return jpa.findByTaskIdOrderBySentAt(taskId).stream().map(AiRequestEntity::toDomain).toList();
    }

    @Override
    public List<AiRequest> withStatus(AiRequest.Status status) {
        return jpa.findByStatus(status).stream().map(AiRequestEntity::toDomain).toList();
    }

    @Override
    public @Nullable Instant lastFinished(Collection<AiRequestType> types) { return jpa.lastFinished(names(types)); }

    @Override
    public boolean anyOfTypes(Collection<AiRequestType> types) { return jpa.existsByTypeIn(names(types)); }

    @Override
    public void lockOrders(String key) { jpa.lockOrders(key); }

    @Override
    public AiRequestPage page(int offset, int limit) {
        var page = jpa.findAll(new OffsetLimit(offset, limit, Sort.by(Sort.Direction.DESC, "sentAt", "id")));
        return new AiRequestPage(page.map(AiRequestEntity::toDomain).getContent(), page.getTotalElements());
    }

    private static List<String> names(Collection<AiRequestType> types) {
        return types.stream().map(Enum::name).toList();
    }
}
