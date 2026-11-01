package com.socialhub.platform.postgres;

import com.socialhub.platform.AppPaths;
import com.socialhub.platform.SocialHubProperties;
import com.socialhub.platform.observability.ExternalCalls;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    DataSource dataSource(EmbeddedPostgres postgres) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.jdbcUrl());
        config.setUsername(EmbeddedPostgres.USER);
        config.setPassword("");
        config.setMaximumPoolSize(8);
        config.setPoolName("socialhub");
        return new HikariDataSource(config);
    }
}
