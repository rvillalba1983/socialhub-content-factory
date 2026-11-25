package com.socialhub.publishing.application;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.PcSchedule;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishRouteId;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.settings.PublishSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Publer: primera vía en TikTok y respaldo en Instagram (donde va antes la API oficial). Su API es de pago, así que SocialHub deja el encargo «Programar en Publer» en
 * {@code para-claude-pc} y Claude lo programa con Chrome en el PC de Raúl.
 * <p>
 * Claude debe hacerlo antes de 8 h de la hora de publicación (la «deadline» del encargo). El motor espera su
 * resultado hasta 7 h antes. Publer depende de las pasadas de Claude con Chrome en el PC (una cada 5 h; de 08:00 a 18:00 solo las
 * urgentes), así que con un margen mínimo corto (30 minutos por defecto) lo habitual es publicar por la API oficial con SocialHub abierto.
 */
@Slf4j
@Component
@Order(1)
class PublerRoute implements PublishRoute {

    static final Duration DEADLINE = Duration.ofHours(8);
    static final Duration GIVE_UP = Duration.ofHours(7);

    private final AiOrders orders;
    private final PublerAccountRepository accounts;
    private final PublerCapacity capacity;
    private final PromotionQueries promotions;
    private final PublishSettings settings;
    private final java.time.Clock clock;

    PublerRoute(AiOrders orders, PublerAccountRepository accounts, PublerCapacity capacity,
                PromotionQueries promotions, PublishSettings settings, java.time.Clock clock) {
        this.orders = orders;
        this.accounts = accounts;
        this.capacity = capacity;
        this.promotions = promotions;
        this.settings = settings;
        this.clock = clock;
    }

    @Override public PublishRouteId id() { return PublishRouteId.PUBLER; }

    @Override public boolean configured() { return true; }

    @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(GIVE_UP); }

    static Instant deadline(Instant scheduledAt) { return scheduledAt.minus(DEADLINE); }

    /**
     * La hora límite del encargo: la más temprana entre las 8 h de Publer y el margen mínimo de publicación de la red (nunca se
     * publica al instante). Es la misma que usa el encargo, para no crear uno que SocialHub cancelaría al enviarlo.
     */
    static Instant limit(Instant scheduledAt, Duration margin) {
        Instant byPubler = deadline(scheduledAt);
        Instant byMargin = scheduledAt.minus(margin);
        return byMargin.isBefore(byPubler) ? byMargin : byPubler;
    }

    /** Cuánto antes de su límite tiene que ser la última pasada de Claude con Chrome que cuente: le deja tiempo de programarla. */
    static final Duration PASS_MARGIN = com.socialhub.ai.domain.ClaudePasses.PASS_MARGIN;
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
        Duration margin = settings.minMarginFor(p.getPlatform().name());
        Instant limit = limit(p.scheduledAt(), margin);
        if (!now.isBefore(limit)) {
            return new Outcome.Failed(margin.compareTo(DEADLINE) > 0
                    ? "queda menos del margen mínimo de publicación (" + margin.toMinutes() + " minutos): no da tiempo a que Claude la programe con Chrome"
                    : "quedan menos de " + DEADLINE.toHours() + " h: no da tiempo a que Claude la programe con Chrome");
        }
        // Las pasadas de Claude con Chrome son cada 5 h: si ninguna que haga este encargo llega antes del límite, ni se pide
        if (!PcSchedule.canMeet(limit, now)) {
            return new Outcome.Failed("ninguna pasada de Claude con Chrome llega antes del límite del encargo (" + DEADLINE.toHours()
                    + " h antes de la hora o el margen mínimo de la red): no da tiempo a que la programe");
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
        // Una sola programación viva por publicación (decisiones 38 y 40): los encargos anteriores que sigan abiertos se
        // cancelan antes de crear el nuevo, para que Claude no programe la misma publicación dos veces
        int superseded = orders.supersede(p.getPromotionId(), AiRequestType.PUBLER_SCHEDULE, p.getId(), null, AiOrders.REPLACED);
        if (superseded > 0) log.info("Programar en Publer de {}: {} encargo(s) anterior(es) cancelado(s) al pedir uno nuevo", p.getId(), superseded);
        String task = orders.submitInternal(p.getPromotionId(), AiRequestType.PUBLER_SCHEDULE,
                "Programar en Publer: " + p.getTitle(),
                "Programa en Publer la publicación indicada, con su texto, hashtags y archivos, a la hora indicada.",
                List.of(p.getId()));
        return new Outcome.Pending(task);
    }

    @Override
    public void withdrawPending(Publication p, PublishJob job) {
        orders.supersede(p.getPromotionId(), AiRequestType.PUBLER_SCHEDULE, p.getId(), null,
                "Retirada porque la publicación ya no está programada");
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
