package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.PcSchedule;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublishJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publer, primera vía. Su API es de pago, así que SocialHub deja el encargo «Programar en Publer» en
 * {@code para-claude-pc} y Claude lo programa con Chrome en el PC de Raúl.
 * <p>
 * Claude debe hacerlo antes de 8 h de la hora de publicación (la «deadline» del encargo). El motor espera su
 * resultado hasta 7 h antes; así queda una hora para probar las otras vías sin bajar del margen de 6 h.
 */
@Component
@Order(1)
class PublerRoute implements PublishRoute {

    static final Duration DEADLINE = Duration.ofHours(8);
    static final Duration GIVE_UP = Duration.ofHours(7);

    private final AiOrders orders;

    PublerRoute(@Lazy AiOrders orders) { this.orders = orders; }

    @Override public Id id() { return Id.PUBLER; }

    @Override public boolean configured() { return true; }

    @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(GIVE_UP); }

    static Instant deadline(Instant scheduledAt) { return scheduledAt.minus(DEADLINE); }

    /** Cuánto antes de su límite tiene que ser la última pasada de Claude con Chrome que cuente: le deja tiempo de programarla. */
    static final Duration PASS_MARGIN = Duration.ofMinutes(10);
    /** Cuánto antes de esa pasada hay que haber cedido el encargo, para que lo vea (lo que tarda en sincronizar Drive y algo más). */
    static final Duration HANDOVER_LEAD = Duration.ofMinutes(30);

    /**
     * El último momento en que una vía anterior puede ceder la publicación a Publer sin que se quede sin una pasada de
     * Claude con Chrome antes del límite de Publer. Normalmente es una hora más que el límite; si el límite cae de
     * noche (la tarea corre de 8:40 a 22:40), es antes de la última pasada anterior.
     */
    static Instant handoverBy(Instant scheduledAt) {
        Instant limit = deadline(scheduledAt);
        Instant pass = PcSchedule.lastPassAtOrBefore(limit.minus(PASS_MARGIN));
        Instant byPass = pass.minus(HANDOVER_LEAD);
        Instant usual = limit.minus(Duration.ofHours(1));
        return byPass.isBefore(usual) ? byPass : usual;
    }

    @Override
    public Outcome schedule(Publication p, PublishJob job, Instant now) {
        if (!now.isBefore(deadline(p.scheduledAt()))) {
            return new Outcome.Failed("quedan menos de " + DEADLINE.toHours() + " h: no da tiempo a que Claude la programe con Chrome");
        }
        String task = orders.submitInternal(p.getPromotionId(), AiRequestType.PUBLER_SCHEDULE,
                "Programar en Publer: " + p.getTitle(),
                "Programa en Publer la publicación «" + p.getTitle() + "» con su texto, hashtags y archivos, a la hora indicada.",
                List.of(p.getId()));
        return new Outcome.Pending(task);
    }
}
