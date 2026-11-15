package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.ExternalCalendarRepository;
import com.socialhub.publishing.domain.ExternalCalendarRepository.Item;
import com.socialhub.publishing.domain.ExternalCalendarRepository.Source;
import com.socialhub.publishing.domain.ExternalCalendarRepository.State;
import com.socialhub.publishing.domain.ExternalCalendarRepository.Status;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PubloraAccountRepository;
import com.socialhub.publishing.domain.PubloraGateway;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import com.socialhub.publishing.domain.SecretStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Calendario con las tres vías (fase 7, bloque 4): lo programado o publicado fuera de SocialHub. Publora se lee por
 * su API cada hora; Publer y lo publicado en la red, con el encargo diario «Leer calendario» a Claude con Chrome.
 */
@Slf4j
@PrimaryPort
@Service
public class ExternalCalendar {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** Ventana que se lee: dos semanas atrás y dos meses adelante (Publora); Publer, dos semanas adelante. */
    static final Duration PAST = Duration.ofDays(14);
    static final Duration FUTURE = Duration.ofDays(60);
    static final int PUBLER_DAYS = 14;
    /** El encargo diario sale desde esta hora (la tarea del PC pasa a las 00:50, 05:50, 10:50, 15:50 y 20:50). */
    static final LocalTime DAILY_FROM = LocalTime.of(7, 0);

    public record View(List<Item> items, List<State> states) {}

    private final ExternalCalendarRepository calendar;
    private final PubloraAccountRepository accounts;
    private final PubloraGateway publora;
    private final SecretStore secrets;
    private final PublishJobRepository jobs;
    private final PromotionQueries promotions;
    private final AiOrders orders;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ExternalCalendar(ExternalCalendarRepository calendar, PubloraAccountRepository accounts, PubloraGateway publora,
                     SecretStore secrets, PublishJobRepository jobs, PromotionQueries promotions, AiOrders orders,
                     TransactionTemplate transactions, ApplicationEventPublisher events, Clock clock) {
        this.calendar = calendar;
        this.accounts = accounts;
        this.publora = publora;
        this.secrets = secrets;
        this.jobs = jobs;
        this.promotions = promotions;
        this.orders = orders;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    /** Lo de fuera de SocialHub de la marca de la promoción entre esos días (hora de España), y cómo va cada fuente. */
    @Transactional(readOnly = true)
    public View view(String promotionId, LocalDate from, LocalDate to) {
        String brand = promotions.brandOf(promotionId);
        return new View(calendar.between(brand, from.atStartOfDay(MADRID).toInstant(), to.plusDays(1).atStartOfDay(MADRID).toInstant()),
                calendar.states(brand));
    }

    /** Lo de fuera que ya ocupa esos días: para las propuestas de la semana. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forAi(String promotionId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Item i : view(promotionId, from, to).items()) {
            if (i.publicationId() != null) continue;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("source", i.source().label());
            m.put("platform", i.platform().name());
            m.put("at", ZonedDateTime.ofInstant(i.at(), MADRID).toLocalDateTime().toString());
            m.put("status", i.status().name());
            if (i.text() != null) m.put("text", i.text().length() > 300 ? i.text().substring(0, 300) + "…" : i.text());
            list.add(m);
        }
        return list;
    }

    // ------------------------------------------------------------------ Reloj

    @Scheduled(initialDelayString = "${socialhub.calendar.initial-delay-millis:90000}",
               fixedDelayString = "${socialhub.calendar.tick-millis:600000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                tick();
            } catch (RuntimeException e) {
                log.warn("Calendario externo: {}", e.toString());
            }
        });
    }

    /** Publora si hace más de una hora; el encargo de Publer y la red si hoy aún no se ha pedido. */
    void tick() {
        Instant now = clock.instant();
        for (String brand : promotions.brandIds()) {
            boolean publoraDue = calendar.state(brand, Source.PUBLORA).map(State::syncedAt)
                    .map(at -> !now.isBefore(at.plus(Duration.ofHours(1)))).orElse(true);
            if (publoraDue && !accounts.accounts(brand).isEmpty() && secrets.get(PubloraRoute.KEY).isPresent()) {
                syncPublora(brand);
            }
            requestDaily(brand);
        }
    }

    /** Lee lo de Publora de las cuentas de la marca. */
    void syncPublora(String brand) {
        Instant now = clock.instant();
        Instant from = now.minus(PAST);
        Instant to = now.plus(FUTURE);
        String key = secrets.get(PubloraRoute.KEY).orElse(null);
        List<PubloraAccountRepository.Account> chosen = accounts.accounts(brand);
        if (key == null || chosen.isEmpty()) return;
        try {
            Map<String, Platform> byConnection = new HashMap<>();
            chosen.forEach(a -> byConnection.put(a.connectionId(), a.platform()));
            Map<String, String> ours = new HashMap<>();
            for (PublishJob job : jobs.withStatus(EnumSet.allOf(PublishJob.Status.class))) {
                if (PublishRoute.Id.PUBLORA.label().equals(job.getRoute()) && job.getExternalId() != null
                        && job.getStatus() != PublishJob.Status.CANCELLED) {
                    ours.put(job.getExternalId(), job.getPublicationId());
                }
            }
            List<Item> items = new ArrayList<>();
            for (PubloraGateway.Listed l : publora.list(key, from, to)) {
                Platform platform = byConnection.get(l.connectionId());
                if (platform == null) continue;
                Status status = switch (l.status().toLowerCase(Locale.ROOT)) {
                    case "published" -> Status.PUBLISHED;
                    case "failed", "partially_published" -> Status.FAILED;
                    case "draft" -> null;
                    default -> Status.SCHEDULED;
                };
                if (status == null) continue;
                items.add(new Item(Source.PUBLORA, platform, l.at(), status, l.content(), null, l.postGroupId(),
                        ours.get(l.postGroupId())));
            }
            transactions.executeWithoutResult(s -> {
                calendar.replace(brand, Source.PUBLORA, from, to, items, now);
                calendar.saveState(brand, new State(Source.PUBLORA, null, null, now, null));
            });
            events.publishEvent(new FactoryChanged("calendar", brand));
        } catch (PubloraGateway.PubloraException e) {
            State before = calendar.state(brand, Source.PUBLORA).orElse(null);
            transactions.executeWithoutResult(s -> calendar.saveState(brand,
                    new State(Source.PUBLORA, null, null, before == null ? null : before.syncedAt(), e.getMessage())));
        }
    }

    /** Una vez al día y por marca, desde las 7:00, el encargo «Leer calendario» para la tarea del PC. */
    void requestDaily(String brand) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(MADRID);
        LocalDate today = now.toLocalDate();
        if (now.toLocalTime().isBefore(DAILY_FROM)) return;
        State state = calendar.state(brand, Source.PUBLER).orElse(null);
        if (state != null && today.equals(state.requestedOn())) return;
        String promotion = activePromotion(brand, today);
        if (promotion == null) return;
        transactions.executeWithoutResult(s -> {
            String task = orders.submitInternal(promotion, AiRequestType.CALENDAR_READ,
                    "Leer calendario · " + today,
                    "Mira lo programado en Publer en los próximos " + PUBLER_DAYS + " días y lo publicado en los perfiles de la marca "
                            + "en las últimas dos semanas, y devuélvelo para el calendario de SocialHub.",
                    List.of(today.toString()));
            calendar.saveState(brand, new State(Source.PUBLER, today, task,
                    state == null ? null : state.syncedAt(), state == null ? null : state.error()));
        });
        log.info("Encargo «Leer calendario» creado para {} ({})", brand, today);
    }

    private @Nullable String activePromotion(String brand, LocalDate day) {
        for (String id : promotions.activeBetween(day, day)) {
            if (brand.equals(promotions.brandOf(id))) return id;
        }
        return null;
    }

    // ------------------------------------------------------------------ Resultado del encargo

    /** Ventanas que cubre el encargo de ese día: Publer hacia delante y la red hacia atrás. */
    static Instant[] publerWindow(LocalDate day) {
        return new Instant[] {day.atStartOfDay(MADRID).toInstant(), day.plusDays(PUBLER_DAYS + 1L).atStartOfDay(MADRID).toInstant()};
    }

    static Instant[] networkWindow(LocalDate day) {
        return new Instant[] {day.minusDays(14).atStartOfDay(MADRID).toInstant(), day.plusDays(1).atStartOfDay(MADRID).toInstant()};
    }

    /** Guarda lo que trajo Claude (dentro de la transacción del lector de resultados). */
    void applyRead(String promotionId, LocalDate day, List<Item> publer, List<Item> network) {
        String brand = promotions.brandOf(promotionId);
        Instant now = clock.instant();
        Instant[] p = publerWindow(day);
        Instant[] n = networkWindow(day);
        calendar.replace(brand, Source.PUBLER, p[0], p[1], publer, now);
        calendar.replace(brand, Source.NETWORK, n[0], n[1], network, now);
        State before = calendar.state(brand, Source.PUBLER).orElse(null);
        calendar.saveState(brand, new State(Source.PUBLER, before == null ? day : before.requestedOn(),
                before == null ? null : before.taskId(), now, null));
        calendar.saveState(brand, new State(Source.NETWORK, null, null, now, null));
        events.publishEvent(new FactoryChanged("calendar", brand));
    }

    void readFailed(String promotionId, String reason) {
        String brand = promotions.brandOf(promotionId);
        State before = calendar.state(brand, Source.PUBLER).orElse(null);
        calendar.saveState(brand, new State(Source.PUBLER, before == null ? null : before.requestedOn(),
                before == null ? null : before.taskId(), before == null ? null : before.syncedAt(), reason));
    }
}
