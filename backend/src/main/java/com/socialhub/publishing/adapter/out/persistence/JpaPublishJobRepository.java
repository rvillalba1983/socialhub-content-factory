package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPublishJobRepository implements PublishJobRepository {

    private final PublishJobJpaRepository jpa;

    @Override
    public Optional<PublishJob> find(String publicationId) {
        return jpa.findById(publicationId).map(JpaPublishJobRepository::toDomain);
    }

    @Override
    public List<PublishJob> withStatus(Collection<PublishJob.Status> statuses) {
        return jpa.findByStatusIn(statuses).stream().map(JpaPublishJobRepository::toDomain).toList();
    }

    @Override
    public void save(PublishJob job) {
        PublishJobEntity e = jpa.findById(job.getPublicationId()).orElse(null);
        if (e == null) {
            e = new PublishJobEntity();
            e.setPublicationId(job.getPublicationId());
        } else if (e.getVersion() != job.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(PublishJobEntity.class, job.getPublicationId());
        }
        e.setStatus(job.getStatus());
        e.setRouteIndex(job.getRouteIndex());
        e.setRoute(job.getRoute());
        e.setTaskId(job.getTaskId());
        e.setLateTaskIds(String.join("\n", job.getLateTaskIds()));
        e.setExternalId(job.getExternalId());
        e.setUrl(job.getUrl());
        e.setNextTryAt(job.getNextTryAt());
        e.setAttempts(job.getAttempts());
        e.setLastError(job.getLastError());
        e.setManualFile(job.getManualFile());
        e.setPublishStartedAt(job.getPublishStartedAt());
        e.setLog(String.join("\n", job.getLog()));
        e.setCreatedAt(job.getCreatedAt());
        e.setUpdatedAt(job.getUpdatedAt());
        jpa.saveAndFlush(e);
    }

    private static PublishJob toDomain(PublishJobEntity e) {
        return new PublishJob(e.getPublicationId(), e.getStatus(), e.getRouteIndex(), e.getRoute(), e.getTaskId(),
                PublishJob.split(e.getLateTaskIds()), e.getExternalId(), e.getUrl(), e.getNextTryAt(), e.getAttempts(),
                e.getLastError(), e.getManualFile(), e.getPublishStartedAt(), PublishJob.split(e.getLog()), e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion());
    }
}
