package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.postgres.ModeDataSources;
import com.socialhub.promotion.domain.PublicShareMemo;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Tabla {@code app_setting} (clave {@code public-share:<marca>}), de la base real sea cual sea el modo. */
@Component
class JdbcPublicShareMemo implements PublicShareMemo {

    private final JdbcClient jdbc;

    JdbcPublicShareMemo(ModeDataSources databases) {
        this.jdbc = JdbcClient.create(databases.of(AppMode.REAL));
    }

    private static String key(String brandId) { return "public-share:" + brandId; }

    @Override
    public Optional<String> markedFor(String brandId) {
        return jdbc.sql("select value from app_setting where key = ?").param(key(brandId)).query(String.class).optional();
    }

    @Override
    public void mark(String brandId, String ordersFolder) {
        jdbc.sql("insert into app_setting (key, value) values (?, ?) on conflict (key) do update set value = excluded.value")
                .param(key(brandId)).param(ordersFolder).update();
    }

    @Override
    public void clear(String brandId) {
        jdbc.sql("delete from app_setting where key = ?").param(key(brandId)).update();
    }
}
