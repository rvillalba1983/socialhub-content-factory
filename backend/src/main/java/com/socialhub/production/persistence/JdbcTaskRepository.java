package com.socialhub.production.persistence;

import com.socialhub.platform.DomainException;
import com.socialhub.production.domain.Agent;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskRepository implements TaskRepository {

    private final JdbcClient jdbc;

    JdbcTaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Task> findById(String id) {
        return jdbc.sql("select * from task where id = ?").param(id).query(this::row).optional();
    }

    @Override
    public List<Task> findByStatus(Collection<TaskStatus> statuses) {
        if (statuses.isEmpty()) return List.of();
        List<String> codes = statuses.stream().map(TaskStatus::code).toList();
        return jdbc.sql("select * from task where status in (:codes) order by id")
                .param("codes", codes).query(this::row).list();
    }

    @Override
    public List<Task> findByStation(String stationId) {
        return jdbc.sql("select * from task where station_id = ? order by id").param(stationId).query(this::row).list();
    }

    @Override
    public Set<String> completedIds() {
        return new HashSet<>(jdbc.sql("select id from task where status = 'ok'").query(String.class).list());
    }

    @Override
    public void save(Task t) {
        int updated = jdbc.sql("""
                update task set status = ?, paused = ?, progress = ?, agent_kind = ?, agent_name = ?, reason = ?,
                       error = ?, result = ?, human = ?, attempts = ?, started_at = ?, updated_at = now(),
                       version = version + 1
                 where id = ? and version = ?
                """)
                .params(t.status().code(), t.paused(), Math.round(t.progress() * 100) / 100.0, t.agent().kind(),
                        t.agent().name(), t.reason(), t.error(), t.result(), t.human(), t.attempts(),
                        t.startedAt() == null ? null : Timestamp.from(t.startedAt()), t.id(), t.version())
                .update();
        if (updated == 0) {
            throw new DomainException("La tarea ha cambiado mientras tanto. Vuelve a intentarlo.");
        }
        jdbc.sql("delete from task_step where task_id = ?").param(t.id()).update();
        for (int i = 0; i < t.steps().size(); i++) {
            Task.Step s = t.steps().get(i);
            jdbc.sql("insert into task_step (task_id, position, label, state) values (?, ?, ?, ?)")
                    .params(t.id(), i, s.label(), s.state()).update();
        }
        jdbc.sql("delete from task_check where task_id = ?").param(t.id()).update();
        for (int i = 0; i < t.checks().size(); i++) {
            Task.Check c = t.checks().get(i);
            jdbc.sql("insert into task_check (task_id, position, label, passed) values (?, ?, ?, ?)")
                    .params(t.id(), i, c.label(), c.passed()).update();
        }
        for (String message : t.pendingEvents()) {
            jdbc.sql("insert into task_event (task_id, message) values (?, ?)").params(t.id(), message).update();
        }
    }

    private Task row(ResultSet rs, int n) throws SQLException {
        String id = rs.getString("id");
        Timestamp started = rs.getTimestamp("started_at");
        List<Task.Step> steps = jdbc.sql("select label, state from task_step where task_id = ? order by position")
                .param(id).query((r, i) -> new Task.Step(r.getString("label"), r.getDouble("state"))).list();
        List<Task.Check> checks = jdbc.sql("select label, passed from task_check where task_id = ? order by position")
                .param(id).query((r, i) -> new Task.Check(r.getString("label"), r.getBoolean("passed"))).list();
        List<String> deps = jdbc.sql("select depends_on from task_dependency where task_id = ? order by depends_on")
                .param(id).query(String.class).list();
        return new Task(id, rs.getString("station_id"), rs.getString("promotion_id"), rs.getString("type"),
                rs.getString("title"), TaskStatus.fromCode(rs.getString("status")), rs.getBoolean("paused"),
                rs.getDouble("progress"), new Agent(rs.getString("agent_kind"), rs.getString("agent_name")),
                rs.getString("reason"), rs.getString("error"), rs.getString("result"), rs.getBoolean("human"),
                rs.getInt("attempts"), rs.getInt("max_attempts"), started == null ? null : started.toInstant(),
                steps, checks, deps, rs.getInt("version"));
    }
}
