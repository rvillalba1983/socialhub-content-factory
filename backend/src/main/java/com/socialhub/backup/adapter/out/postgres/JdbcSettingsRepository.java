package com.socialhub.backup.adapter.out.postgres;

import com.socialhub.backup.domain.SettingsRepository;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.postgres.ModeDataSources;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Tabla {@code app_setting} de la base de datos real, sea cual sea el modo elegido en la interfaz. */
@Component
class JdbcSettingsRepository implements SettingsRepository {

    private final JdbcClient jdbc;

    JdbcSettingsRepository(ModeDataSources databases) {
        this.jdbc = JdbcClient.create(databases.of(AppMode.REAL));
    }

    @Override
    public Optional<String> get(String key) {
        return jdbc.sql("select value from app_setting where key = ?").param(key).query(String.class).optional();
    }

    @Override
    public void put(String key, String value) {
        jdbc.sql("insert into app_setting (key, value) values (?, ?) on conflict (key) do update set value = excluded.value")
                .param(key).param(value).update();
    }

    @Override
    public void delete(String key) {
        jdbc.sql("delete from app_setting where key = ?").param(key).update();
    }
}
