package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.PublishJob;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PublishJobJpaRepository extends JpaRepository<PublishJobEntity, String> {
    List<PublishJobEntity> findByStatusIn(Collection<PublishJob.Status> statuses);
}
