package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.MetricsRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaMetricsRepository implements MetricsRepository {

    private final MetricsJpaRepository jpa;

    @Override
    public Optional<Metrics> find(String publicationId) { return jpa.findById(publicationId).map(JpaMetricsRepository::toDomain); }

    @Override
    public List<Metrics> all() { return jpa.findAll().stream().map(JpaMetricsRepository::toDomain).toList(); }

    @Override
    public void save(Metrics m) {
        jpa.save(new MetricsEntity(m.publicationId(), m.status(), m.taskId(), m.attempts(), m.requestedAt(), m.measuredAt(),
                m.views(), m.reach(), m.likes(), m.comments(), m.saves(), m.shares(), m.note()));
    }

    private static Metrics toDomain(MetricsEntity e) {
        return new Metrics(e.getPublicationId(), e.getStatus(), e.getTaskId(), e.getAttempts(), e.getRequestedAt(),
                e.getMeasuredAt(), e.getViews(), e.getReach(), e.getLikes(), e.getComments(), e.getSaves(), e.getShares(),
                e.getNote());
    }
}
