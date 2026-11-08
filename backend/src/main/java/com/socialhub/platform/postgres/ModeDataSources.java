package com.socialhub.platform.postgres;

import com.socialhub.platform.AppMode;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;

/** Las conexiones a la base de datos de cada modo. Solo para quien necesite un modo concreto (los datos de ejemplo). */
public final class ModeDataSources implements AutoCloseable {

    private final Map<AppMode, HikariDataSource> pools;

    ModeDataSources(Map<AppMode, HikariDataSource> pools) {
        this.pools = Map.copyOf(pools);
    }

    public DataSource of(AppMode mode) { return pools.get(mode); }

    /** Dirección JDBC de la base de datos de un modo, para quien necesite conexiones propias (restaurar copias). */
    public String jdbcUrl(AppMode mode) { return pools.get(mode).getJdbcUrl(); }

    /** Usuario de PostgreSQL de SocialHub (sin contraseña: solo escucha en 127.0.0.1). */
    public String user() { return EmbeddedPostgres.USER; }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
    }
}
