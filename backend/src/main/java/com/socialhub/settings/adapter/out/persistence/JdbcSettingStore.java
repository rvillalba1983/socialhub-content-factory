package com.socialhub.settings.adapter.out.persistence;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.postgres.ModeDataSources;
import com.socialhub.settings.domain.SettingStore;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Tabla {@code app_setting} de la base de datos real, sea cual sea el modo elegido en la interfaz. */
@Component
class JdbcSettingStore implements SettingStore {

    private final JdbcClient jdbc;

    JdbcSettingStore(ModeDataSources databases) {
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
}
