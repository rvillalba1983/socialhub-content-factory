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

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
    }
}
