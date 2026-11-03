package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.AutoRequestRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaAutoRequestRepository implements AutoRequestRepository {

    private final AutoRequestJpaRepository jpa;

    @Override
    public boolean exists(String publicationId, String kind, String fingerprint) {
        return jpa.existsById(new AutoRequestEntity.Key(publicationId, kind, fingerprint));
    }

    @Override
    public void add(String publicationId, String kind, String fingerprint, String taskId, Instant at) {
        jpa.save(new AutoRequestEntity(publicationId, kind, fingerprint, taskId, at));
    }
}
