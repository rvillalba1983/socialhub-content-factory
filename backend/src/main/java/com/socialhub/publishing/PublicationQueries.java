package com.socialhub.publishing;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicationQueries {

    private final JdbcClient jdbc;

    public PublicationQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record PublicationView(String id, String promotionId, String platform, String type, String title,
                                  LocalDate date, LocalTime time, String timezone, String status, String route,
                                  String flag, String note, String confirmation, boolean demo) {}

    public List<PublicationView> all() {
        return jdbc.sql("select * from publication order by scheduled_on, scheduled_time, id")
                .query((rs, n) -> new PublicationView(rs.getString("id"), rs.getString("promotion_id"),
                        rs.getString("platform"), rs.getString("type"), rs.getString("title"),
                        rs.getObject("scheduled_on", LocalDate.class), rs.getObject("scheduled_time", LocalTime.class),
                        rs.getString("timezone"), rs.getString("status"), rs.getString("route"), rs.getString("flag"),
                        rs.getString("note"), rs.getString("confirmation"), rs.getBoolean("is_demo")))
                .list();
    }

    public List<PublicationView> byPromotion(String promotionId) {
        return all().stream().filter(p -> p.promotionId().equals(promotionId)).toList();
    }
}
