package com.socialhub.production.persistence;

import com.socialhub.platform.DomainException;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStationRepository implements StationRepository {

    private final JdbcClient jdbc;

    JdbcStationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Station> findById(String id) {
        return jdbc.sql("select * from station where id = ?").param(id).query(this::row).optional();
    }

    @Override
    public List<Station> findAll() {
        return jdbc.sql("select * from station order by position").query(this::row).list();
    }

    @Override
    public void save(Station s) {
        int updated = jdbc.sql("""
                update station set status = ?, previous_status = ?, paused = ?, note = ?, version = version + 1
                 where id = ? and version = ?
                """).params(s.status(), s.previousStatus(), s.paused(), s.note(), s.id(), s.version()).update();
        if (updated == 0) throw new DomainException("La estación ha cambiado mientras tanto. Vuelve a intentarlo.");
    }

    private Station row(ResultSet rs, int n) throws SQLException {
        return new Station(rs.getString("id"), rs.getString("status"), rs.getString("previous_status"),
                rs.getBoolean("paused"), rs.getString("note"), rs.getInt("version"));
    }
}
