package com.socialhub.platform.live.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.socialhub.platform.AppModeHolder;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;

class LiveUpdatesTest {

    @Test
    void closingTheContextEndsEveryLiveConnection() {
        LiveUpdates live = new LiveUpdates(mock(AppModeHolder.class));
        live.subscribe();
        live.subscribe();
        assertThat(live.subscribers()).isEqualTo(2);

        live.onClose(new ContextClosedEvent(new GenericApplicationContext()));

        // Sin conexiones abiertas, Tomcat no espera 30 s al apagarse
        assertThat(live.subscribers()).isZero();
    }
}
