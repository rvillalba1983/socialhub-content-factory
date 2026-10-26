package com.socialhub.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Auditoría de negocio: quién hizo qué, sobre qué y cuándo. Distinta de los registros técnicos. */
@Service
public class AuditLog {

    private final JdbcClient jdbc;

    public AuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String actor, String action, String targetType, String targetId, String details) {
        jdbc.sql("insert into audit_event (actor, action, target_type, target_id, details) values (?, ?, ?, ?, ?)")
                .params(actor, action, targetType, targetId, details).update();
    }

    public List<Entry> latest(int limit) {
        return jdbc.sql("select at, actor, action, target_type, target_id, details from audit_event order by id desc limit ?")
                .param(limit)
                .query((rs, n) -> new Entry(rs.getTimestamp("at").toInstant(), rs.getString("actor"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"), rs.getString("details")))
                .list();
    }

    public record Entry(Instant at, String actor, String action, String targetType, String targetId, String details) {}
}
