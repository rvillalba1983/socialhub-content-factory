package com.socialhub.armory;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agentes de IA, herramientas locales y servicios que usa la fábrica, con su disponibilidad. */
@Service
@Transactional(readOnly = true)
public class ArmoryQueries {

    private final JdbcClient jdbc;

    public ArmoryQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param kind agent, tool, service o device */
    public record ArmoryItem(String id, String kind, String name, String description, String status, String note,
                             List<String> capabilities, boolean demo) {}

    public List<ArmoryItem> items() {
        return jdbc.sql("""
                select * from (
                  select id, 'agent' kind, name, description, status, note, position, is_demo from ai_agent
                  union all
                  select id, 'tool', name, description, status, note, position, is_demo from local_tool
                  union all
                  select id, kind, name, description, status, note, position, is_demo from external_service
                ) a order by position, name
                """).query((rs, n) -> {
            String id = rs.getString("id");
            String kind = rs.getString("kind");
            List<String> caps = "tool".equals(kind)
                    ? jdbc.sql("select capability from local_tool_capability where tool_id = ? order by capability")
                            .param(id).query(String.class).list()
                    : List.of();
            return new ArmoryItem(id, kind, rs.getString("name"), rs.getString("description"), rs.getString("status"),
                    rs.getString("note"), caps, rs.getBoolean("is_demo"));
        }).list();
    }
}
