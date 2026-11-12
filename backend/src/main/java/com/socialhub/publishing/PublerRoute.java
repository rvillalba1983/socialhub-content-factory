package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.ai.PcSchedule;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.PublishJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publer: primera vía en TikTok y respaldo en Instagram (donde va antes la API oficial). Su API es de pago, así que SocialHub deja el encargo «Programar en Publer» en
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
    private final PublerAccountRepository accounts;
    private final PublerCapacity capacity;
    private final PromotionQueries promotions;
    private final java.time.Clock clock;

    PublerRoute(@Lazy AiOrders orders, PublerAccountRepository accounts, PublerCapacity capacity,
                PromotionQueries promotions, java.time.Clock clock) {
        this.orders = orders;
        this.accounts = accounts;
        this.capacity = capacity;
        this.promotions = promotions;
        this.clock = clock;
    }

    @Override public Id id() { return Id.PUBLER; }

    @Override public boolean configured() { return true; }

    @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(GIVE_UP); }

    static Instant deadline(Instant scheduledAt) { return scheduledAt.minus(DEADLINE); }

    /** Cuánto antes de su límite tiene que ser la última pasada de Claude con Chrome que cuente: le deja tiempo de programarla. */
    static final Duration PASS_MARGIN = com.socialhub.ai.ClaudePasses.PASS_MARGIN;
    /** Cuánto antes de esa pasada hay que haber cedido el encargo, para que lo vea (lo que tarda en sincronizar Drive y algo más). */
    static final Duration HANDOVER_LEAD = Duration.ofMinutes(30);

    /**
     * El último momento en que una vía anterior puede ceder la publicación a Publer sin que se quede sin una pasada de
     * Claude con Chrome antes del límite de Publer. Es 30 minutos antes de la última pasada del PC que cabe antes
     * del límite (con 10 minutos de margen), y nunca menos de una hora antes del límite: entre 9 h y unas 13 h 40 min antes
     * de la publicación (las pasadas son cada 5 h).
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
        // Las pasadas de Claude con Chrome son cada 5 h: si ninguna que haga este encargo llega antes del límite, ni se pide
        if (!PcSchedule.canMeet(deadline(p.scheduledAt()), now)) {
            return new Outcome.Failed("ninguna pasada de Claude con Chrome llega antes del límite de Publer (" + DEADLINE.toHours()
                    + " h antes de la hora): no da tiempo a que la programe");
        }
        // Sin la cuenta de Publer de la marca en esa red no se crea el encargo: Claude no sabría dónde programarla
        String brandId = promotions.brandOf(p.getPromotionId());
        String account = accounts.account(brandId, p.getPlatform()).orElse(null);
        if (account == null) {
            return new Outcome.Held(missingAccount(brandName(brandId), p.getPlatform()));
        }
        // Plan gratuito de Publer: tope de publicaciones programadas a la vez por cuenta
        if (capacity.full(account, p.getPlatform(), p.getId(), clock.instant())) {
            return new Outcome.Held("La cuenta «" + account + "» de Publer ya tiene " + capacity.limit()
                    + " publicaciones programadas (límite del plan gratuito). Queda en espera hasta que salga alguna");
        }
        String task = orders.submitInternal(p.getPromotionId(), AiRequestType.PUBLER_SCHEDULE,
                "Programar en Publer: " + p.getTitle(),
                "Programa en Publer la publicación «" + p.getTitle() + "» con su texto, hashtags y archivos, a la hora indicada.",
                List.of(p.getId()));
        return new Outcome.Pending(task);
    }

    /** Motivo de la retención cuando la marca no tiene cuenta de Publer en esa red. */
    static String missingAccount(String brandName, com.socialhub.publishing.domain.Platform platform) {
        return "Falta la cuenta de Publer de " + brandName + " para " + (platform == com.socialhub.publishing.domain.Platform.INSTAGRAM ? "Instagram" : "TikTok");
    }

    private String brandName(String brandId) {
        return promotions.brandRefs().stream().filter(b -> b.id().equals(brandId)).map(PromotionQueries.BrandRef::name)
                .findFirst().orElse(brandId);
    }
}
