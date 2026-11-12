package com.socialhub.publishing;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.ExternalCalendarRepository.Item;
import com.socialhub.publishing.domain.ExternalCalendarRepository.Source;
import com.socialhub.publishing.domain.ExternalCalendarRepository.Status;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublerAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * «Leer calendario»: Claude, con Chrome en el PC de Raúl, mira lo programado en Publer (dos semanas adelante) y lo
 * publicado en los perfiles de la marca (dos semanas atrás). Devuelve {@code outputs.items}; lo que corresponde a
 * una publicación de SocialHub lleva su id.
 */
@Component
class CalendarReadHandler implements AiTypeHandler {

    static final int MAX_ITEMS = 300;

    private final PublishingQueries queries;
    private final PromotionQueries promotions;
    private final ExternalCalendar calendar;
    private final PublerAccountRepository accounts;

    CalendarReadHandler(PublishingQueries queries, PromotionQueries promotions, @Lazy ExternalCalendar calendar,
                        PublerAccountRepository accounts) {
        this.queries = queries;
        this.promotions = promotions;
        this.calendar = calendar;
        this.accounts = accounts;
    }

    @Override public AiRequestType type() { return AiRequestType.CALENDAR_READ; }

    @Override
    public void validateOrder(String promotionId, List<String> inputs) {
        if (inputs.size() != 1) throw new IllegalArgumentException("El encargo lleva el día (aaaa-mm-dd)");
        day(inputs.getFirst());
    }

    @Override
    public Map<String, Object> inputs(AiJob job) {
        LocalDate day = day(job.inputs().getFirst());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", Map.of("id", job.brandId(), "handle", handle(job.brandId())));
        Map<String, String> publerAccounts = new LinkedHashMap<>();
        accounts.accounts(job.brandId()).forEach((platform, name) -> publerAccounts.put(platform.name(), name));
        m.put("publer_accounts", publerAccounts);
        m.put("publer_window", window(ExternalCalendar.publerWindow(day)));
        m.put("network_window", window(ExternalCalendar.networkWindow(day)));
        m.put("socialhub_publications", ours(job.promotionId(), day));
        m.put("how", List.of(
                "Abre Publer en Chrome con la sesión de Raúl y apunta lo programado dentro de publer_window en las cuentas de Publer de la marca («publer_accounts», por red; la de TikTok puede ser la misma para las dos marcas).",
                "Abre los perfiles de Instagram y TikTok de la marca y apunta lo publicado dentro de network_window.",
                "Si algo coincide con una de socialhub_publications (misma red y hora, mismo texto), pon su id en publication_id.",
                "Solo lectura: no programes, no publiques, no borres, no comentes y no des me gusta. No inicies sesión por Raúl."));
        m.put("expected_outputs", "items: [{source (publer | network), platform (INSTAGRAM | TIKTOK), at (ISO 8601 con zona), "
                + "status (scheduled | published | failed), text (inicio del texto), url (si la hay), external_id (si se ve), "
                + "publication_id (si es de SocialHub)}] y summary");
        return m;
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        LocalDate day = day(job.inputs().getFirst());
        JsonNode items = outputs.path("items");
        if (!items.isArray()) throw new IllegalArgumentException("Falta «outputs.items» (una lista, vacía si no hay nada)");
        if (items.size() > MAX_ITEMS) throw new IllegalArgumentException("Demasiados elementos (máximo " + MAX_ITEMS + ")");
        Set<String> known = new HashSet<>();
        ours(job.promotionId(), day).forEach(p -> known.add((String) p.get("id")));
        List<Item> publer = new ArrayList<>();
        List<Item> network = new ArrayList<>();
        int n = 0;
        for (JsonNode i : items) {
            n++;
            String source = i.path("source").asString("").toLowerCase(Locale.ROOT);
            Source s = switch (source) {
                case "publer" -> Source.PUBLER;
                case "network", "instagram", "tiktok" -> Source.NETWORK;
                default -> throw new IllegalArgumentException("Elemento " + n + ": «source» tiene que ser publer o network");
            };
            Platform platform;
            try {
                platform = Platform.valueOf(i.path("platform").asString("").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Elemento " + n + ": «platform» tiene que ser INSTAGRAM o TIKTOK");
            }
            Instant at;
            try {
                at = OffsetDateTime.parse(i.path("at").asString("")).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Elemento " + n + ": «at» no es una fecha ISO 8601 con zona");
            }
            Status status = switch (i.path("status").asString("").toLowerCase(Locale.ROOT)) {
                case "scheduled" -> Status.SCHEDULED;
                case "published" -> Status.PUBLISHED;
                case "failed" -> Status.FAILED;
                default -> throw new IllegalArgumentException("Elemento " + n + ": «status» tiene que ser scheduled, published o failed");
            };
            String url = text(i.path("url"));
            if (url != null && !url.startsWith("https://")) throw new IllegalArgumentException("Elemento " + n + ": el enlace no es https");
            String publication = text(i.path("publication_id"));
            if (publication != null && !known.contains(publication)) publication = null;
            String content = text(i.path("text"));
            if (content != null && content.length() > 500) content = content.substring(0, 500) + "…";
            Item item = new Item(s, platform, at, status, content, url, text(i.path("external_id")), publication);
            (s == Source.PUBLER ? publer : network).add(item);
        }
        calendar.applyRead(job.promotionId(), day, publer, network);
        String summary = outputs.path("summary").isString() ? outputs.path("summary").asString().strip() : "";
        return "Calendario leído: " + publer.size() + " en Publer y " + network.size() + " publicadas en la red"
                + (summary.isEmpty() ? "" : " · " + summary);
    }

    @Override
    public void onFailed(AiJob job, String reason) {
        calendar.readFailed(job.promotionId(), reason);
    }

    private List<Map<String, Object>> ours(String promotionId, LocalDate day) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : promotions.promotionIdsOf(promotions.brandOf(promotionId))) {
            for (Publication p : queries.between(id, day.minusDays(14), day.plusDays(ExternalCalendar.PUBLER_DAYS))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("platform", p.getPlatform().name());
                m.put("at", ZonedDateTime.ofInstant(p.scheduledAt(), ExternalCalendar.MADRID).toOffsetDateTime().toString());
                m.put("title", p.getTitle());
                m.put("status", p.getStatus().name());
                if (p.getCaption() != null) m.put("text", p.getCaption().length() > 120 ? p.getCaption().substring(0, 120) + "…" : p.getCaption());
                list.add(m);
            }
        }
        return list;
    }

    private String handle(String brandId) {
        return promotions.handleOf(brandId);
    }

    private static Map<String, String> window(Instant[] w) {
        return Map.of("from", w[0].toString(), "to", w[1].toString());
    }

    private static LocalDate day(String text) {
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Día no válido: " + text);
        }
    }

    private static @Nullable String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String s = node.asString("").strip();
        return s.isEmpty() ? null : s;
    }
}
