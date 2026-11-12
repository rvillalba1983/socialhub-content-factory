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

    List<TaskEntity> findByTypeAndPromotionIdAndStatusInOrderById(String type, String promotionId, Collection<TaskStatus> statuses);

    List<TaskEntity> findByStatusInOrderById(Collection<TaskStatus> statuses);

    @Query(value = """
            select t.id from task t join station s on s.id = t.station_id
            where t.status = 'QUEUED' and t.type in (:types) and not s.paused and s.status <> 'BLOCKED'
            order by t.updated_at, t.id
            limit :limit
            for update of t skip locked""", nativeQuery = true)
    List<String> lockQueued(Collection<String> types, int limit);

    @Query("select t.id from TaskEntity t where t.status = com.socialhub.production.domain.TaskStatus.COMPLETED")
    Set<String> findCompletedIds();
}
