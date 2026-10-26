package com.socialhub.production;

import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskAction;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecturas del módulo de producción para la interfaz. */
@Service
@Transactional(readOnly = true)
public class ProductionQueries {

    private final JdbcClient jdbc;
    private final TaskRepository tasks;

    public ProductionQueries(JdbcClient jdbc, TaskRepository tasks) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    public record StationView(String id, int position, String name, String description, String status, boolean paused,
                              String note, String limit, List<String> tools, Counts counts) {}

    public record Counts(int total, int running, int waiting, int blocked, int errors, int completed, int idle,
                         int cancelled) {}

    public record ActionView(String id, String label, boolean confirm) {}

    public record StepView(String label, double state) {}

    public record CheckView(String label, boolean passed) {}

    public record EventView(Instant at, String message) {}

    public record TaskView(String id, String stationId, String promotionId, String type, String title, String goal,
                           String status, boolean paused, double progress, String agentKind, String agentName, String why,
                           String reason, String error, String result, boolean human, int attempts, int maxAttempts,
                           Instant startedAt, List<String> inputs, List<StepView> steps, List<CheckView> checks,
                           List<String> dependsOn, List<EventView> events, List<ActionView> actions, boolean demo) {}

    public List<StationView> stations() {
        Map<String, Counts> counts = countsByStation();
        return jdbc.sql("select * from station order by position").query((rs, n) -> {
            String id = rs.getString("id");
            return new StationView(id, rs.getInt("position"), rs.getString("name"), rs.getString("description"),
                    rs.getString("status"), rs.getBoolean("paused"), rs.getString("note"), rs.getString("limit_note"),
                    Arrays.asList((String[]) rs.getArray("tools").getArray()),
                    counts.getOrDefault(id, new Counts(0, 0, 0, 0, 0, 0, 0, 0)));
        }).list();
    }

    public List<TaskView> tasks() {
        Map<String, List<String>> inputs = jdbc.sql("select task_id, text from task_input order by task_id, position")
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2))).list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<String, List<EventView>> events = jdbc.sql("select task_id, at, message from task_event order by task_id, at, id")
                .query((rs, n) -> Map.entry(rs.getString(1), new EventView(rs.getTimestamp(2).toInstant(), rs.getString(3))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<String, String[]> extra = jdbc.sql("select id, goal, why, is_demo from task")
                .query((rs, n) -> Map.entry(rs.getString(1), new String[] {rs.getString(2), rs.getString(3), rs.getString(4)}))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return tasks.findByStatus(EnumSet.allOf(TaskStatus.class)).stream()
                .map(t -> view(t, inputs.getOrDefault(t.id(), List.of()), events.getOrDefault(t.id(), List.of()),
                        extra.get(t.id())))
                .toList();
    }

    private TaskView view(Task t, List<String> inputs, List<EventView> events, String[] extra) {
        List<ActionView> actions = t.allowedActions().stream()
                .map(a -> new ActionView(a.code(), a.label(), a.needsConfirmation()))
                .toList();
        return new TaskView(t.id(), t.stationId(), t.promotionId(), t.type(), t.title(), extra[0], t.status().code(),
                t.paused(), t.progress(), t.agent().kind(), t.agent().name(), extra[1], t.reason(), t.error(), t.result(),
                t.human(), t.attempts(), t.maxAttempts(), t.startedAt(), inputs,
                t.steps().stream().map(s -> new StepView(s.label(), s.state())).toList(),
                t.checks().stream().map(c -> new CheckView(c.label(), c.passed())).toList(),
                t.dependsOn(), events, actions, "t".equals(extra[2]) || "true".equals(extra[2]));
    }

    private Map<String, Counts> countsByStation() {
        return jdbc.sql("""
                select station_id,
                       count(*) total,
                       count(*) filter (where status = 'run') running,
                       count(*) filter (where status in ('wait', 'queued')) waiting,
                       count(*) filter (where status = 'block') blocked,
                       count(*) filter (where status = 'err') errors,
                       count(*) filter (where status = 'ok') completed,
                       count(*) filter (where status = 'idle') idle,
                       count(*) filter (where status = 'cancel') cancelled
                  from task group by station_id
                """).query((rs, n) -> Map.entry(rs.getString(1), new Counts(rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Códigos de las acciones existentes, para validar la API. */
    public static List<String> actionCodes() {
        return Arrays.stream(TaskAction.values()).map(TaskAction::code).toList();
    }
}
