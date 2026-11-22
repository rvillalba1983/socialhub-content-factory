package com.socialhub.publishing.application;

import com.socialhub.platform.DomainException;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PublishingQueries.DuplicateRef;
import com.socialhub.publishing.PublishingQueries.Insight;
import com.socialhub.publishing.PublishingQueries.MoveReason;
import com.socialhub.publishing.PublishingQueries.SlotProposal;
import com.socialhub.publishing.domain.DuplicateDetector;
import com.socialhub.publishing.domain.ExternalCalendarRepository;
import com.socialhub.publishing.domain.FreeSlot;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationOutcome;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lo que se ve de cada publicación además de sus datos: su resultado (con solo datos reales), sus posibles duplicados y si
 * puede moverse a la siguiente hora libre. Lo calcula todo en cada consulta, sin guardar nada.
 */
@Component
@RequiredArgsConstructor
class PublicationInsights {

    /** Las que aún se pueden mover: de camino a publicar o con error de publicación. */
    static final Set<PublicationStatus> MOVABLE = EnumSet.of(PublicationStatus.IN_PRODUCTION, PublicationStatus.DRAFT,
            PublicationStatus.IN_REVIEW, PublicationStatus.APPROVED, PublicationStatus.PUBLISH_ERROR);

    private final PublicationRepository publications;
    private final PublishJobRepository jobs;
    private final ExternalCalendarRepository external;
    private final PromotionQueries promotions;
    private final com.socialhub.settings.PublishSettings settings;
    private final Clock clock;

    /** Marca de cada promoción, en una sola consulta. */
    Map<String, String> brandsByPromotion() {
        Map<String, String> brands = new HashMap<>();
        promotions.promotionRefs().forEach(r -> brands.put(r.id(), r.brandId()));
        return brands;
    }

    private static String key(String brand, Publication p) { return brand + "|" + p.getPlatform(); }

    /** Lo calculado hace un momento: una lectura de la fábrica lo pide varias veces y no hace falta rehacerlo cada vez. */
    private record Snapshot(Instant at, Map<String, Insight> insights, List<Publication> publications,
                          Map<String, String> brands) {}

    private static final Duration FRESH = Duration.ofSeconds(2);
    private volatile @org.jspecify.annotations.Nullable Snapshot cache;

    /** Algo ha cambiado en la fábrica (ya confirmado): lo guardado deja de valer. */
    @org.springframework.transaction.event.TransactionalEventListener(fallbackExecution = true)
    void on(com.socialhub.platform.live.FactoryChanged changed) { cache = null; }

    private Snapshot snapshot() {
        Instant now = clock.instant();
        Snapshot current = cache;
        if (current != null && !now.isBefore(current.at()) && Duration.between(current.at(), now).compareTo(FRESH) < 0) return current;
        Snapshot fresh = compute(now);
        cache = fresh;
        return fresh;
    }

    Map<String, Insight> all() { return snapshot().insights(); }

    /** La marca de cada promoción, tal como se leyó. */
    Map<String, String> brandsRead() { return snapshot().brands(); }

    /** Las publicaciones que se leyeron para calcularlo (solo las de promociones conocidas). */
    List<Publication> publicationsRead() { return snapshot().publications(); }

    private Snapshot compute(Instant now) {
        Map<String, String> brands = brandsByPromotion();
        List<Publication> all = publications.findAll().stream().filter(p -> brands.containsKey(p.getPromotionId())).toList();
        Map<String, PublishJob> jobById = new HashMap<>();
        jobs.withStatus(EnumSet.allOf(PublishJob.Status.class)).forEach(j -> jobById.put(j.getPublicationId(), j));
        Map<String, List<ExternalCalendarRepository.Item>> outside = outside(all, brands, now);
        Map<String, List<DuplicateRef>> duplicates = new HashMap<>();
        for (var group : DuplicateDetector.find(all, brands::get)) {
            for (String id : group.publicationIds()) {
                for (String other : group.publicationIds()) {
                    if (!other.equals(id)) duplicates.computeIfAbsent(id, k -> new ArrayList<>()).add(new DuplicateRef(other, group.reason()));
                }
            }
        }
        Map<String, List<Publication>> byBrandNetwork = new HashMap<>();
        for (Publication p : all) {
            if (p.getStatus() != PublicationStatus.CANCELLED) {
                byBrandNetwork.computeIfAbsent(key(brands.get(p.getPromotionId()), p), k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, Insight> result = new HashMap<>();
        for (Publication p : all) {
            var outcome = PublicationOutcome.of(p, jobById.get(p.getId()), outside.getOrDefault(p.getId(), List.of()), now);
            var same = byBrandNetwork.getOrDefault(key(brands.get(p.getPromotionId()), p), List.of());
            result.put(p.getId(), new Insight(outcome, duplicates.getOrDefault(p.getId(), List.of()),
                    moveReason(p, instantsOfOthers(p, same), now)));
        }
        return new Snapshot(now, result, all, brands);
    }

    /** Lo que ha leído Claude de fuera de SocialHub, por publicación (solo lo que ya está asociado a una). */
    private Map<String, List<ExternalCalendarRepository.Item>> outside(List<Publication> all, Map<String, String> brands, Instant now) {
        Map<String, List<ExternalCalendarRepository.Item>> byPublication = new HashMap<>();
        // Solo hace falta lo de fuera de las que ya salieron o ya pasaron su hora
        all = all.stream().filter(p -> p.getStatus() == PublicationStatus.PUBLISHED || !p.scheduledAt().isAfter(now)).toList();
        if (all.isEmpty()) return byPublication;
        Instant from = all.stream().map(Publication::scheduledAt).min(Instant::compareTo).orElseThrow().minus(Duration.ofDays(1));
        Instant to = all.stream().map(Publication::scheduledAt).max(Instant::compareTo).orElseThrow().plus(Duration.ofDays(2));
        for (String brand : all.stream().map(p -> brands.get(p.getPromotionId())).collect(java.util.stream.Collectors.toSet())) {
            for (var item : external.between(brand, from, to)) {
                if (item.publicationId() != null) byPublication.computeIfAbsent(item.publicationId(), k -> new ArrayList<>()).add(item);
            }
        }
        return byPublication;
    }

    private static List<Instant> instantsOfOthers(Publication p, Collection<Publication> sameBrandNetwork) {
        return sameBrandNetwork.stream().filter(o -> !o.getId().equals(p.getId())).map(Publication::scheduledAt).toList();
    }

    private Optional<MoveReason> moveReason(Publication p, Collection<Instant> others, Instant now) {
        Duration margin = settings.minMarginFor(p.getPlatform().name());
        if (!MOVABLE.contains(p.getStatus())) return Optional.empty();
        if (p.scheduledAt().isBefore(now.plus(margin))) return Optional.of(MoveReason.LOST_MARGIN);
        if (FreeSlot.clashes(p.scheduledAt(), others, FreeSlot.SPACING)) return Optional.of(MoveReason.CLASH);
        return Optional.empty();
    }

    /** La siguiente hora libre de la publicación; falla si no se puede mover o no hace falta. */
    SlotProposal proposal(Publication p) {
        Instant now = clock.instant();
        if (!MOVABLE.contains(p.getStatus())) {
            throw new DomainException(p.getStatus() == PublicationStatus.SCHEDULED
                    ? "Está programada: para moverla, vuelve a «Aprobada» y prográmala otra vez."
                    : "No se puede mover una publicación «" + p.getStatus().label() + "».");
        }
        String brand = promotions.brandOf(p.getPromotionId());
        List<Instant> others = new ArrayList<>();
        for (Publication o : publications.findAll()) {
            if (o.getId().equals(p.getId()) || o.getStatus() == PublicationStatus.CANCELLED || o.getPlatform() != p.getPlatform()) continue;
            if (brand.equals(promotions.brandOf(o.getPromotionId()))) others.add(o.scheduledAt());
        }
        var reason = moveReason(p, others, now)
                .orElseThrow(() -> new DomainException("No hace falta moverla: tiene margen y no choca con otra publicación."));
        ZoneId zone = ZoneId.of(p.getTimezone());
        Duration margin = settings.minMarginFor(p.getPlatform().name());
        Instant start = p.scheduledAt().isAfter(now.plus(margin)) ? p.scheduledAt() : now.plus(margin);
        var slot = FreeSlot.next(start, zone, others, FreeSlot.SPACING).atZone(zone);
        var endsOn = promotions.datesOf(p.getPromotionId()).endsOn();
        return new SlotProposal(p.getId(), reason, p.getScheduledOn(), p.getScheduledTime(), slot.toLocalDate(),
                slot.toLocalTime(), p.getTimezone(), !slot.toLocalDate().equals(p.getScheduledOn()), endsOn,
                endsOn != null && slot.toLocalDate().isAfter(endsOn), margin.toMinutes());
    }
}
