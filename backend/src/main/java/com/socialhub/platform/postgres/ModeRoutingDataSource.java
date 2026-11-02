package com.socialhub.platform.postgres;

import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import java.util.HashMap;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/** Cada conexión nueva va a la base de datos del modo actual. Una transacción en curso conserva la suya. */
class ModeRoutingDataSource extends AbstractRoutingDataSource {

    private final AppModeHolder mode;

    ModeRoutingDataSource(ModeDataSources pools, AppModeHolder mode) {
        this.mode = mode;
        var targets = new HashMap<Object, Object>();
        for (AppMode m : AppMode.values()) targets.put(m, pools.of(m));
        setTargetDataSources(targets);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return mode.current();
    }
}
