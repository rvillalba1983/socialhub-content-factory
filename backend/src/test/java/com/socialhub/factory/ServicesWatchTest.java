package com.socialhub.factory;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiSentOrders.Sent;
import com.socialhub.ai.PcSchedule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServicesWatchTest {

    private static Instant madrid(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(PcSchedule.ZONE).toInstant();
    }

    private final Instant now = madrid(9, 12, 30);

    @Test
    void pcIsOkWithItsLastResult() {
        var r = ServicesWatch.pc(Optional.of(madrid(9, 11, 42)), true, List.of(), now);
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.note()).isEqualTo("Último resultado: 9/11/2026 11:42");
    }

    @Test
    void pcFailsWhenUrgentOrdersAreUnansweredNinetyMinutesAfterTheirPass() {
        // Urgentes enviados a las 9:00 y a las 9:30: les tocaba la pasada de las 10:50, y a las 12:20 ya van tarde
        var waiting = List.of(new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 9, 0), true, true, true),
                new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 9, 30), true, true, true));
        var r = ServicesWatch.pc(Optional.of(madrid(8, 20, 0)), true, waiting, now);
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.note()).isEqualTo("2 encargos sin contestar desde las 9:00. ¿Está activa la tarea «Publicar con Chrome» y abierto Chrome?");
        // Con uno solo, en singular; y uno enviado hace menos de 90 minutos no cuenta
        var one = List.of(new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 9, 30), true, true, true),
                new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 11, 0), true, true, true));
        assertThat(ServicesWatch.pc(Optional.empty(), true, one, now).note()).startsWith("1 encargo sin contestar desde las 9:30");
        var fresh = List.of(new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 11, 0), true, true, true));
        assertThat(ServicesWatch.pc(Optional.of(madrid(8, 20, 0)), true, fresh, now).status()).isEqualTo("OK");
    }

    @Test
    void pcOrderThatCanWaitIsNotLateUntilTheNightPassIsOver() {
        // Enviado a las 23:00 sin prisa: le toca la pasada de las 0:50 y va tarde pasadas las 2:20
        var night = List.of(new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(8, 23, 0), true, true));
        assertThat(ServicesWatch.pc(Optional.empty(), true, night, madrid(9, 2, 19)).status()).isEqualTo("WAITING");
        assertThat(ServicesWatch.pc(Optional.empty(), true, night, madrid(9, 2, 20)).status()).isEqualTo("FAILED");
    }

    @Test
    void pcAndCloudAreNeutralWithoutOrdersAndWaitingBeforeTheFirstResult() {
        assertThat(ServicesWatch.pc(Optional.empty(), false, List.of(), now)).isEqualTo(new ServicesWatch.Result("IDLE", "Sin encargos todavía"));
        assertThat(ServicesWatch.cloud(Optional.empty(), false, List.of(), now)).isEqualTo(new ServicesWatch.Result("IDLE", "Sin encargos todavía"));
        var fresh = List.of(new Sent("p", AiRequestType.PUBLISH_CHECK, madrid(9, 11, 50), true, true));
        assertThat(ServicesWatch.pc(Optional.empty(), true, fresh, now)).isEqualTo(new ServicesWatch.Result("WAITING", "Esperando el primer resultado"));
    }

    @Test
    void cloudIsOkWithItsLastResultAndWaitingWhenAPickupWasMissed() {
        assertThat(ServicesWatch.cloud(Optional.of(madrid(9, 9, 45)), true, List.of(), now))
                .isEqualTo(new ServicesWatch.Result("OK", "Último resultado: 9/11/2026 09:45"));
        // Urgente enviado a las 9:30 (pasada de las 10:30) y ya son más de las 11:00; los del PC no cuentan
        var waiting = List.of(new Sent("p", AiRequestType.CAPTIONS, madrid(9, 9, 30), false, true, true),
                new Sent("p", AiRequestType.PUBLER_SCHEDULE, madrid(9, 9, 30), true, true, true));
        var r = ServicesWatch.cloud(Optional.of(madrid(8, 21, 0)), true, waiting, now);
        assertThat(r.status()).isEqualTo("WAITING");
        assertThat(r.note()).startsWith("1 encargo sin resultado desde las 9:30");
    }

    @Test
    void publoraConnectedWithoutKeyOrNotUsed() {
        assertThat(ServicesWatch.publora(true, false)).isEqualTo(new ServicesWatch.Result("OK", "Conectada"));
        assertThat(ServicesWatch.publora(true, true).status()).isEqualTo("OK");
        assertThat(ServicesWatch.publora(false, false)).isEqualTo(new ServicesWatch.Result("WAITING", "Sin clave"));
        assertThat(ServicesWatch.publora(false, true)).isEqualTo(new ServicesWatch.Result("IDLE", "No se usa"));
    }
}
