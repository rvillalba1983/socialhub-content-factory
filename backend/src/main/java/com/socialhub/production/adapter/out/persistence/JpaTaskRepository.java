package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskFilter;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaTaskRepository implements TaskRepository {

    private final TaskJpaRepository jpa;
    private final ProductionPersistenceMapper mapper;

    @Override
    public Optional<Task> findById(String id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Task> findAll(TaskFilter filter) {
        Specification<TaskEntity> spec = Specification.unrestricted();
        if (filter.stationId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("stationId"), filter.stationId()));
        }
        if (filter.promotionId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("promotionId"), filter.promotionId()));
        }
        if (!filter.statuses().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("status").in(filter.statuses()));
        }
        if (filter.human() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("human"), filter.human()));
        }
        return jpa.findAll(spec, Sort.by("stationId", "id")).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Task> findByStation(String stationId) {
        return jpa.findByStationIdOrderById(stationId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Task> findByStatusIn(Collection<TaskStatus> statuses) {
        return jpa.findByStatusInOrderById(statuses).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Set<String> completedIds() {
        return jpa.findCompletedIds();
    }

    @Override
    public Task save(Task task) {
        var existing = jpa.findById(task.getId());
        TaskEntity entity = existing.orElseGet(() -> new TaskEntity(task.getId()));
        mapper.update(task, entity);
        task.newHistory().forEach(h -> entity.getEvents().add(new TaskEventEntity(entity, h.at(), h.message())));
        // Una entidad ya cargada se guarda sola al confirmar la transacción (sin merge)
        if (existing.isEmpty()) jpa.save(entity);
        task.clearPending();
        return task;
    }
}
