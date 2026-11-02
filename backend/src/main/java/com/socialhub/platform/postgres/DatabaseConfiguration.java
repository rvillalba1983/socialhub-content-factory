package com.socialhub.platform.postgres;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * PostgreSQL incluido con una base de datos por modo (real y demo). La aplicación usa un único {@link DataSource}
 * que, en cada conexión, apunta a la base de datos del modo actual. Flyway migra las dos al arrancar.
 */
@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    EmbeddedPostgres embeddedPostgres(AppPaths paths, SocialHubProperties properties, ExternalCalls calls) {
        SocialHubProperties.Postgres pg = properties.postgres();
        Path runtime = pg.runtimeDir() == null || pg.runtimeDir().isBlank() ? paths.runtime() : Path.of(pg.runtimeDir());
        PostgresBinaries binaries = calls.call("postgres", "extract-binaries", runtime.toString(),
                () -> PostgresBinaries.resolve(pg.binariesDir(), runtime.toAbsolutePath()));
        return new EmbeddedPostgres(binaries, paths.postgresData(), paths.logs(), pg.port(), pg.startTimeoutSeconds(), calls);
    }

    @Bean(destroyMethod = "close")
    ModeDataSources modeDataSources(EmbeddedPostgres postgres, ExternalCalls calls) {
        Map<AppMode, HikariDataSource> pools = new EnumMap<>(AppMode.class);
        for (AppMode mode : AppMode.values()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(postgres.jdbcUrl(mode.database()));
            config.setUsername(EmbeddedPostgres.USER);
            config.setPassword("");
            config.setMaximumPoolSize(mode == AppMode.REAL ? 8 : 4);
            config.setPoolName("socialhub-" + mode.name().toLowerCase());
            HikariDataSource pool = new HikariDataSource(config);
            calls.run("postgres", "migrate", mode.database(), () -> Flyway.configure()
                    .dataSource(pool)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate());
            pools.put(mode, pool);
        }
        return new ModeDataSources(pools);
    }

    @Bean
    @Primary
    DataSource dataSource(ModeDataSources pools, AppModeHolder mode) {
        return new ModeRoutingDataSource(pools, mode);
    }
}
