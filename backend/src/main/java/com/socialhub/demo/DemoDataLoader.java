package com.socialhub.demo;

import com.socialhub.audit.AuditLog;
import com.socialhub.platform.SocialHubProperties;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Carga los datos de ejemplo (marcados con is_demo) la primera vez que arranca SocialHub con la base de
 * datos vacía. Las fechas se colocan alrededor del día de la carga.
 */
@Component
@Order(0)
class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final String RESOURCE = "demo/demo-data.json";

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final SocialHubProperties properties;
    private final AuditLog audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    DemoDataLoader(JdbcClient jdbc, ObjectMapper mapper, SocialHubProperties properties, AuditLog audit, Clock clock,
                   PlatformTransactionManager transactions) {
        this.tx = new TransactionTemplate(transactions);
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.demo().enabled()) return;
        if (jdbc.sql("select count(*) from brand").query(Integer.class).single() > 0) return;
        DemoData data;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            data = mapper.readValue(in, DemoData.class);
        }
        tx.executeWithoutResult(status -> load(data));
    }

    private void load(DemoData data) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        for (int i = 0; i < data.commonRules().size(); i++) {
            jdbc.sql("insert into app_rule (position, text) values (?, ?)").params(i, data.commonRules().get(i)).update();
        }
        for (int i = 0; i < data.brands().size(); i++) {
            var b = data.brands().get(i);
            jdbc.sql("insert into brand (id, name, handle, color, position, is_demo) values (?, ?, ?, ?, ?, true)")
                    .params(b.id(), b.name(), b.handle(), b.color(), i).update();
        }
        for (int i = 0; i < data.promotions().size(); i++) {
            insertPromotion(data.promotions().get(i), i, today);
        }
        for (int i = 0; i < data.stations().size(); i++) {
            var s = data.stations().get(i);
            jdbc.sql("""
                    insert into station (id, position, name, description, status, note, limit_note, tools)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """).params(s.id(), i, s.name(), s.description(), s.status(), s.note(), s.limit(),
                    s.tools().toArray(String[]::new)).update();
        }
        for (var t : data.tasks()) {
            insertTask(t, now);
        }
        for (var t : data.tasks()) {
            for (String dep : nz(t.deps())) {
                jdbc.sql("insert into task_dependency (task_id, depends_on) values (?, ?)").params(t.id(), dep).update();
            }
        }
        insertArmory(data.armory());
        insertPublications(data, today);
        audit.record("SocialHub", "demo.load", "app", null, "Datos de ejemplo cargados alrededor del " + today);
        log.info("Datos de ejemplo cargados (marcados como demostración)");
    }

    private void insertPromotion(DemoData.PromotionSeed p, int position, LocalDate today) {
        jdbc.sql("""
                insert into promotion (id, brand_id, name, description, scope, cadence, starts_on, ends_on, metrics_source,
                                       position, is_demo)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                """).params(p.id(), p.brandId(), p.name(), p.description(), p.scope(), p.cadence(),
                today.plusDays(p.startOffset()), today.plusDays(p.endOffset()), p.metricsSource(), position).update();
        texts("promotion_goal", p.id(), p.goals());
        texts("promotion_rule", p.id(), p.rules());
        texts("promotion_human_todo", p.id(), p.humanTodos());
        LocalDate start = today.plusDays(p.startOffset());
        for (int i = 0; i < p.milestones().size(); i++) {
            var m = p.milestones().get(i);
            jdbc.sql("insert into promotion_milestone (promotion_id, position, starts_on, ends_on, label) values (?, ?, ?, ?, ?)")
                    .params(p.id(), i, start.plusDays(m.start()), m.end() == null ? null : start.plusDays(m.end()), m.label())
                    .update();
        }
        for (int i = 0; i < p.resources().size(); i++) {
            var r = p.resources().get(i);
            jdbc.sql("insert into promotion_resource (promotion_id, position, kind, location, description) values (?, ?, ?, ?, ?)")
                    .params(p.id(), i, r.kind(), r.location(), r.description()).update();
        }
        for (int i = 0; i < p.automations().size(); i++) {
            var a = p.automations().get(i);
            jdbc.sql("""
                    insert into promotion_automation (promotion_id, position, name, schedule, model, runs_on, status)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """).params(p.id(), i, a.name(), a.schedule(), a.model(), a.runsOn(), a.status()).update();
        }
        for (int i = 0; i < p.routes().size(); i++) {
            var r = p.routes().get(i);
            jdbc.sql("insert into promotion_route (promotion_id, position, name, status, label, notes) values (?, ?, ?, ?, ?, ?)")
                    .params(p.id(), i, r.name(), r.status(), r.label(), r.notes()).update();
        }
        for (int i = 0; i < p.settings().size(); i++) {
            var s = p.settings().get(i);
            jdbc.sql("insert into promotion_setting (promotion_id, position, label, value, origin) values (?, ?, ?, ?, ?)")
                    .params(p.id(), i, s.label(), s.value(), s.origin()).update();
        }
    }

    private void insertTask(DemoData.TaskSeed t, Instant now) {
        Integer started = t.startedMinutesAgo();
        jdbc.sql("""
                insert into task (id, station_id, promotion_id, type, title, goal, status, progress, agent_kind, agent_name,
                                  why, reason, error, result, human, attempts, max_attempts, started_at, is_demo)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                """).params(t.id(), t.station(), t.promotion(), t.type() == null ? "generic" : t.type(), t.title(),
                Objects.requireNonNullElse(t.goal(), ""), t.status(), t.progress() == null ? 0 : t.progress(),
                t.agent().get(0), t.agent().get(1), t.why(), t.reason(), t.error(), t.result(),
                Boolean.TRUE.equals(t.human()), t.attempts() == null ? 1 : t.attempts(),
                t.maxAttempts() == null ? 2 : t.maxAttempts(),
                started == null ? null : Timestamp.from(now.minus(started, ChronoUnit.MINUTES))).update();
        texts("task_input", t.id(), nz(t.inputs()), "task_id");
        List<List<Object>> steps = nz(t.steps());
        for (int i = 0; i < steps.size(); i++) {
            jdbc.sql("insert into task_step (task_id, position, label, state) values (?, ?, ?, ?)")
                    .params(t.id(), i, steps.get(i).get(0), ((Number) steps.get(i).get(1)).doubleValue()).update();
        }
        List<List<Object>> checks = nz(t.checks());
        for (int i = 0; i < checks.size(); i++) {
            jdbc.sql("insert into task_check (task_id, position, label, passed) values (?, ?, ?, ?)")
                    .params(t.id(), i, checks.get(i).get(0), checks.get(i).get(1)).update();
        }
        for (List<Object> e : nz(t.events())) {
            jdbc.sql("insert into task_event (task_id, at, message) values (?, ?, ?)")
                    .params(t.id(), Timestamp.from(now.minus(((Number) e.get(0)).longValue(), ChronoUnit.MINUTES)), e.get(1))
                    .update();
        }
    }

    private void insertArmory(List<DemoData.ArmorySeed> items) {
        jdbc.sql("insert into ai_provider (id, name) values ('anthropic', 'Anthropic')").update();
        for (String model : List.of("Haiku", "Sonnet", "Opus")) {
            jdbc.sql("insert into ai_model (id, provider_id, name) values (?, 'anthropic', ?)")
                    .params("claude-" + model.toLowerCase(), "Claude " + model).update();
        }
        for (int i = 0; i < items.size(); i++) {
            var a = items.get(i);
            switch (a.kind()) {
                case "agent" -> jdbc.sql("""
                        insert into ai_agent (id, provider_id, name, description, status, note, position, is_demo)
                        values (?, ?, ?, ?, ?, ?, ?, true)
                        """).params(a.id(), "claude-pro".equals(a.id()) ? "anthropic" : null, a.name(), a.description(),
                        a.status(), a.note(), i).update();
                case "tool" -> {
                    jdbc.sql("""
                            insert into local_tool (id, name, description, status, note, position, is_demo)
                            values (?, ?, ?, ?, ?, ?, true)
                            """).params(a.id(), a.name(), a.description(), a.status(), a.note(), i).update();
                    for (String c : nz(a.capabilities())) {
                        jdbc.sql("insert into local_tool_capability (tool_id, capability) values (?, ?)").params(a.id(), c).update();
                    }
                }
                default -> jdbc.sql("""
                        insert into external_service (id, name, description, kind, status, note, position, is_demo)
                        values (?, ?, ?, ?, ?, ?, ?, true)
                        """).params(a.id(), a.name(), a.description(), a.kind(), a.status(), a.note(), i).update();
            }
        }
    }

    /** Calendario de ejemplo, generado como en el prototipo pero alrededor de hoy. */
    private void insertPublications(DemoData data, LocalDate today) {
        String[] series = {"Un poema, una historia", "Conoce a Lito y Pachi", "¿Lo has encontrado?", "Detrás de las páginas"};
        LocalDate delitoStart = today.plusDays(promotion(data, "delito-lanzamiento").startOffset());
        LocalDate freeDay = delitoStart.plusDays(18);
        int seq = 0;
        Object[][] week = {{0, "19:00", "Carrusel"}, {1, "13:00", "Historia"}, {2, "19:00", "Carrusel"}, {3, "13:00", "Historia"},
                {4, "19:00", "Carrusel"}};
        for (int w = 1; w <= 12; w++) {
            LocalDate monday = delitoStart.plusDays(7L * (w - 1));
            for (int k = 0; k < week.length; k++) {
                int offset = (int) week[k][0];
                String type = (String) week[k][2];
                LocalDate date = monday.plusDays(offset);
                String status = w == 1 ? (offset < 2 ? "review" : "production") : w == 2 ? "proposal" : "idea";
                String title = "Historia".equals(type) ? "Historia con la llamada a la acción escrita"
                        : (w == 1 && offset == 0 ? "Presentación: 30 poemas que se leen… y se juegan"
                        : series[(w + k) % 4] + " · semana " + w);
                if (date.equals(freeDay)) {
                    title = "Días gratis: empieza hoy (hora de España y de América)";
                    status = "proposal";
                }
                publication("d" + (++seq), "delito-lanzamiento", "instagram", type, title, date, (String) week[k][1], status,
                        "Publer", null, null, null);
            }
        }
        publication("d" + (++seq), "delito-lanzamiento", "instagram", "Historia", "Días gratis: segundo día",
                freeDay.plusDays(1), "13:00", "proposal", "Publer", null, null, null);
        publication("d" + (++seq), "delito-lanzamiento", "instagram", "Historia", "Días gratis: último día",
                freeDay.plusDays(2), "13:00", "proposal", "Publer", null, null, null);

        LocalDate litoStart = today.plusDays(promotion(data, "litoques-lanzamiento").startOffset());
        int todayN = (int) ChronoUnit.DAYS.between(litoStart, today) + 1;
        String confirmed = "Vista semanal de Publer (dato de ejemplo)";
        for (int n = 1; n <= 15; n++) {
            LocalDate date = litoStart.plusDays(n - 1);
            int k = n - todayN;
            String reel;
            String story;
            if (k < 0) { reel = story = "published"; }
            else if (k == 0) { reel = story = "scheduled"; }
            else if (k == 1) { reel = "scheduled"; story = "approved"; }
            else if (k == 2) { reel = "approved"; story = "draft"; }
            else if (k == 3) { reel = story = "production"; }
            else { reel = story = n <= todayN + 5 ? "proposal" : "idea"; }
            String note = k < 0 ? "Confirmada en la vista semanal de Publer" : null;
            publication("l" + n + "r", "litoques-lanzamiento", "instagram", "Reel", "Día " + n + " · Reel de 25 s con un tema de Litoques",
                    date, "19:00", reel, "Publer", null, note, k < 0 ? confirmed : null);
            publication("l" + n + "h", "litoques-lanzamiento", "instagram", "Historia", "Día " + n + " · Historia", date, "21:00",
                    story, "Publer", k == 1 ? "Pendiente de programar: Publer no respondió" : null, note,
                    k < 0 ? confirmed : null);
        }
        LocalDate tiktokDay = delitoStart.plusDays(7);
        publication("tt-delito", "delito-lanzamiento", "tiktok", "TikTok · prueba privada",
                "Primera prueba del adaptador de TikTok (solo visible para la cuenta)", tiktokDay, "12:00", "idea",
                "TikTok · API oficial", "Sin cuenta todavía", null, null);
        publication("tt-litoques", "litoques-lanzamiento", "tiktok", "TikTok · prueba privada",
                "Primera prueba del adaptador de TikTok (solo visible para la cuenta)", tiktokDay, "12:00", "idea",
                "TikTok · API oficial", "Sin cuenta todavía", null, null);
    }

    private void publication(String id, String promotionId, String platform, String type, String title, LocalDate date,
                             String time, String status, String route, String flag, String note, String confirmation) {
        jdbc.sql("""
                insert into publication (id, promotion_id, platform, type, title, scheduled_on, scheduled_time, status, route,
                                         flag, note, confirmation, is_demo)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                """).params(id, promotionId, platform, type, title, date, LocalTime.parse(time), status, route, flag, note,
                confirmation).update();
    }

    private static DemoData.PromotionSeed promotion(DemoData data, String id) {
        return data.promotions().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private void texts(String table, String promotionId, List<String> values) {
        texts(table, promotionId, values, "promotion_id");
    }

    private void texts(String table, String ownerId, List<String> values, String ownerColumn) {
        for (int i = 0; i < values.size(); i++) {
            jdbc.sql("insert into " + table + " (" + ownerColumn + ", position, text) values (?, ?, ?)")
                    .params(ownerId, i, values.get(i)).update();
        }
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
