package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.TaskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TaskJpaRepository extends JpaRepository<TaskEntity, String>, JpaSpecificationExecutor<TaskEntity> {

    List<TaskEntity> findByStationIdOrderById(String stationId);

    List<TaskEntity> findByStatusInOrderById(Collection<TaskStatus> statuses);

    @Query("select t.id from TaskEntity t where t.status = com.socialhub.production.domain.TaskStatus.COMPLETED")
    Set<String> findCompletedIds();
}
