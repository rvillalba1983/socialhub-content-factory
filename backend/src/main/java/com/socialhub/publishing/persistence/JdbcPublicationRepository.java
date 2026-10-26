package com.socialhub.publishing.persistence;

import com.socialhub.platform.DomainException;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPublicationRepository implements PublicationRepository {

    private final JdbcClient jdbc;

    JdbcPublicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Publication> findByPromotionAndStatus(String promotionId, PublicationStatus status) {
        return jdbc.sql("select * from publication where promotion_id = ? and status = ? order by scheduled_on, scheduled_time")
                .params(promotionId, status.code()).query(this::row).list();
    }

    @Override
    public List<Publication> findFlagged(String promotionId) {
        return jdbc.sql("select * from publication where promotion_id = ? and flag is not null and route = 'Publer' order by scheduled_on")
                .param(promotionId).query(this::row).list();
    }

    @Override
    public void save(Publication p) {
        int updated = jdbc.sql("""
                update publication set status = ?, flag = ?, external_id = ?, confirmation = ?, published_at = ?,
                       updated_at = now(), version = version + 1
                 where id = ? and version = ?
                """).params(p.status().code(), p.flag(), p.externalId(), p.confirmation(),
                        p.publishedAt() == null ? null : Timestamp.from(p.publishedAt()), p.id(), p.version())
                .update();
        if (updated == 0) throw new DomainException("La publicación ha cambiado mientras tanto. Vuelve a intentarlo.");
    }

    private Publication row(ResultSet rs, int n) throws SQLException {
        Timestamp at = rs.getTimestamp("published_at");
        return new Publication(rs.getString("id"), rs.getString("promotion_id"),
                PublicationStatus.fromCode(rs.getString("status")), rs.getString("flag"), rs.getString("external_id"),
                rs.getString("confirmation"), at == null ? null : at.toInstant(), rs.getInt("version"));
    }
}
