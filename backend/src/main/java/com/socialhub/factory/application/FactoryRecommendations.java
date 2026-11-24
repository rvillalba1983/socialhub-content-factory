package com.socialhub.factory.application;

import com.socialhub.ai.AiAutoStarted;
import com.socialhub.ai.AiHeldOrders;
import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.ClaudeActivity;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.ai.domain.ClaudePickups;
import com.socialhub.backup.Backups;
import com.socialhub.brandkit.BrandKits;
import com.socialhub.factory.domain.Recommendation;
import com.socialhub.pieces.ContentGaps;
import com.socialhub.pieces.MissingMaterial;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.InstagramOAuth;
import com.socialhub.publishing.PublerAccounts;
import com.socialhub.publishing.PublishingQueries;
import com.socialhub.publishing.PubloraConnection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * «Qué hacer ahora»: la lista completa de recomendaciones, calculadas en cada consulta y sin guardarlas, así que
 * desaparecen solas cuando dejan de hacer falta. El frontal enseña las 3 primeras y el resto con «Ver N más». Por orden de prioridad:
 * <ol>
 *   <li>Marca sin forma de publicar: tiene publicaciones de camino a publicar (en producción, borrador, en revisión o
 *   aprobadas) en los próximos 7 días, la tarea del PC «Publicar con Chrome» no contesta y Publora no se puede usar
 *   para su red (sin clave, sin cuenta de esa red o «No uso Publora»). Va la primera, con la siguiente, porque
 *   bloquea publicaciones reales. Botón a la tarjeta de Publora de la marca si falta algo que Raúl puede conectar; sin
 *   botón si ha elegido no usarla.</li>
 *   <li>Marca con promoción activa sin cuenta de Publer en alguna red, o con publicaciones retenidas por eso: una fila por
 *   marca y red, con botón a la tarjeta «Cuenta en Publer» de la ficha. Va tras las de publicación: bloquea publicaciones reales.</li>
 *   <li>La tarea «Publicar con Chrome» no contesta: encargos de {@code para-claude-pc} sin resultado desde hace 90
 *   minutos después de la pasada que les tocaba (cada 5 h, desde las 0:50). Sin botón.</li>
 *   <li>La conexión con Instagram («Conectar con Instagram») caduca en 10 días o menos, o ya caducó: botón a la tarjeta
 *   «Instagram · API oficial», donde está «Renovar conexión». Si hay clave de usuario del sistema, se dice que mientras tanto
 *   se usará esa. Sin conexión guardada no sale nada.</li>
 *   <li>Propuestas de una promoción retenidas, con el motivo (la marca sin carpeta de «Encargos», la carpeta no
 *   disponible porque Drive está cerrado o el límite diario alcanzado, este último con botón a «Claude»). Una sola
 *   fila por promoción, con este orden de motivos.</li>
 *   <li>Propuestas listas para revisar, con su contador.</li>
 *   <li>Publicación en producción sin contenido (sin piezas y fuera de cualquier spec abierto) que sale en menos de 3
 *   días: «{título} no tiene contenido», con botón «Pedir contenido ya» (un spec urgente a mano de esa publicación o de las
 *   de su promoción en la misma situación). Una fila por promoción.</li>
 *   <li>Marca sin todas sus carpetas.</li>
 *   <li>Marca sin kit aprobado: «Genera el kit de X» (si tiene alguna promoción y no hay kit pendiente ni encargo de kit abierto) o
 *   «Revisa el kit de X» (kit pendiente de aprobación), con botón a su ficha.</li>
 *   <li>Encargo sin resultado tras la recogida que le tocaba (5 minutos de sincronización y 30 de margen), si la
 *   carpeta de «Encargos» de su marca está disponible.</li>
 *   <li>Marca sin cuenta de Publora, salvo que Raúl haya marcado «No uso Publora» para ella.</li>
 *   <li>Sin carpeta de copias.</li>
 *   <li>Marca sin ninguna promoción activa ni planificada por empezar (esa se activa sola): dar de alta la primera, o activar una en pausa.</li>
 *   <li>Propuestas pedidas y en camino, con la hora de la próxima recogida.</li>
 *   <li>Promoción activada sola hoy: el día en que SocialHub la pasa de «Planificada» a «Activa» (empieza hoy), una fila
 *   informativa, sin botón, que desaparece al día siguiente (hora de España). No sale si Raúl la activó a mano. Va la
 *   última: es solo un aviso.</li>
 * </ol>
 * Solo tiene sentido en modo real; quien lo llama decide.
 */
@Component
@RequiredArgsConstructor
class FactoryRecommendations {

    private final AiHeldOrders heldOrders;
    private final AiSentOrders sentOrders;
    private final PromotionQueries promotions;
    private final PublishingQueries publishing;
    private final PubloraConnection publora;
    private final PublerAccounts publer;
    private final Backups backups;
    private final AiAutoStarted autoStarted;
    private final ContentGaps contentGaps;
    private final MissingMaterial missingMaterial;
    private final InstagramOAuth instagramOAuth;
    private final Clock clock;
    private final com.socialhub.settings.PublishSettings settings;
    private final BrandKits brandKits;

    List<Recommendation> compute() {
        Instant now = clock.instant();
        var brands = promotions.brandRefs();
        var allPromotions = promotions.promotionRefs();
        Map<String, PromotionQueries.PromotionRef> promotionById = allPromotions.stream()
                .collect(Collectors.toMap(PromotionQueries.PromotionRef::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        java.time.LocalDate today = now.atZone(ClaudePickups.ZONE).toLocalDate();
        List<AiSentOrders.Sent> sent = sentOrders.waiting();
        List<Recommendation> all = new ArrayList<>();

        // 0. Publicar: la tarea del PC no contesta y, para alguna marca, tampoco hay Publora utilizable
        var pcSilence = ClaudeActivity.pcSilence(sent, now);
        if (pcSilence.isPresent()) {
            var unroutable = publishing.withoutPubloraRoute(today, today.plusDays(7), promotions::brandOf);
            for (var brand : brands) noPublishingRoute(brand, promotionById, unroutable).ifPresent(all::add);
            all.add(pcTaskSilent(pcSilence.get(), now));
        }

        // 0b. Publer: publicaciones retenidas porque la marca no tiene su cuenta de Publer en esa red
        var brandNames = brands.stream().collect(Collectors.toMap(PromotionQueries.BrandRef::id, PromotionQueries.BrandRef::name, (a, b) -> a));
        // Con promoción activa, falta la cuenta de alguna red aunque aún no haya nada retenido (decisión de Raúl, 12 nov)
        Map<String, PublerAccounts.Missing> missingPubler = new LinkedHashMap<>();
        for (var m : publer.missing()) missingPubler.put(m.brandId() + "|" + m.platform(), m);
        for (var brand : brands) {
            if (allPromotions.stream().noneMatch(p -> p.brandId().equals(brand.id()) && p.active())) continue;
            for (var m : publer.unset(brand.id())) missingPubler.putIfAbsent(m.brandId() + "|" + m.platform(), m);
        }
        for (var m : missingPubler.values()) {
            String name = brandNames.getOrDefault(m.brandId(), m.brandId());
            all.add(recommendation("publer-account:" + m.brandId() + ":" + m.platform(),
                    Recommendation.Kind.PUBLER_ACCOUNT_MISSING,
                    "Indica la cuenta de Publer de " + name + " para " + m.networkLabel(),
                    m.held() == 0 ? "Sin la cuenta no se pueden programar en Publer las publicaciones de esa red"
                            : m.held() + (m.held() == 1 ? " publicación retenida" : " publicaciones retenidas")
                            + ": sin la cuenta no se puede programar en Publer",
                    Recommendation.Screen.PUBLER, m.brandId(), null));
        }

        // 0c. Instagram: la conexión con Meta caduca pronto o ya caducó
        instagramOAuth.notice().ifPresent(n -> all.add(instagramExpiring(n)));

        // 0c. Publicaciones de camino a publicar a las que ya no les queda el margen mínimo de publicación (o cuya hora ya pasó)
        // Cada red con su margen: TikTok tiene el suyo, así que sus avisos van aparte (otra fila por promoción)
        Map<String, List<PublishingQueries.WithoutMargin>> tooSoon = publishing.withoutMargin(now).stream()
                .filter(w -> promotionById.containsKey(w.promotionId()))
                .collect(Collectors.groupingBy(w -> w.promotionId() + (isTikTok(w) ? "|" + com.socialhub.settings.PublishSettings.TIKTOK : ""),
                        LinkedHashMap::new, Collectors.toList()));
        tooSoon.values().forEach(list -> {
            var promotion = promotionById.get(list.getFirst().promotionId());
            if (!promotion.active() && !promotion.plannedAhead(today)) return;
            boolean tiktok = isTikTok(list.getFirst());
            all.add(noMargin(promotion, list, now, settings.minMarginFor(list.getFirst().platform()), tiktok));
        });

        // 0d. Posibles duplicados: dos o más publicaciones activas que se repiten (aviso, no bloquea)
        for (var group : publishing.duplicateGroups()) {
            var promotion = promotionById.get(group.promotionId());
            if (promotion == null) continue;
            all.add(duplicates(group, promotion));
        }

        // 1. Propuestas retenidas, o paradas por Drive o por el límite diario
        Map<String, AiHeldOrders.Held> held = new LinkedHashMap<>();
        for (var h : heldOrders.current()) {
            if (h.isPromotionProposals() && promotionById.containsKey(h.promotionId())) held.putIfAbsent(h.promotionId(), h);
        }
        held.forEach((promotionId, h) -> all.add(heldRow(promotionById.get(promotionId), h.reason(),
                Recommendation.Screen.BRAND_FOLDERS)));
        var stalled = heldOrders.stalledProposals().stream()
                .filter(s -> promotionById.containsKey(s.promotionId()))
                .sorted(Comparator.comparing(AiHeldOrders.Stalled::cause)) // Drive antes que el límite
                .toList();
        Set<String> alreadyRow = new java.util.HashSet<>(held.keySet());
        for (var s : stalled) {
            if (!alreadyRow.add(s.promotionId())) continue;
            var promotion = promotionById.get(s.promotionId());
            all.add(s.cause() == AiHeldOrders.Cause.DRIVE_UNAVAILABLE
                    ? heldRow(promotion, "La carpeta de Encargos no está disponible en este ordenador. ¿Está abierto Google Drive?", null)
                    : heldRow(promotion, "Límite diario de encargos a Claude alcanzado (" + s.limit() + "): salen mañana",
                            Recommendation.Screen.CLAUDE));
        }

        // 2. Propuestas para revisar
        Map<String, Long> toReview = publishing.proposalCounts();
        for (var promotion : allPromotions) {
            long n = toReview.getOrDefault(promotion.id(), 0L);
            if (n == 0) continue;
            all.add(recommendation("proposals-review:" + promotion.id(), Recommendation.Kind.PROPOSALS_TO_REVIEW,
                    "Tienes " + n + (n == 1 ? " propuesta" : " propuestas") + " de «" + promotion.name() + "» para revisar",
                    null, Recommendation.Screen.PROPOSALS, promotion.brandId(), promotion.id()).promotionId(promotion.id()));
        }

        // 2b. Publicaciones en producción sin contenido que salen en menos de 3 días (y fuera de cualquier spec abierto)
        for (var promotion : allPromotions) {
            if (!promotion.active() && !promotion.plannedAhead(today)) continue;
            var gaps = contentGaps.of(promotion.id());
            if (gaps.isEmpty() || !gaps.getFirst().scheduledAt().isBefore(now.plus(ContentGaps.SOON))) continue;
            var first = gaps.getFirst();
            var when = first.scheduledAt().atZone(ClaudePickups.ZONE);
            var dto = recommendation("content-missing:" + promotion.id(), Recommendation.Kind.CONTENT_MISSING,
                    first.title() + " no tiene contenido",
                    "Sale el " + when.getDayOfMonth() + " de " + when.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"))
                            + " a las " + hour(when) + ". «Pedir contenido ya» pide un spec urgente de "
                            + (gaps.size() == 1 ? "esa publicación" : "las " + gaps.size() + " publicaciones de «" + promotion.name()
                            + "» que están así"),
                    null, null, null).promotionId(promotion.id())
                    .publicationIds(gaps.stream().map(ContentGaps.Gap::publicationId).toList());
            all.add(dto);
        }

        // 2c. Publicaciones que van a producirse sin imagen ni vídeo propio (informativo: no bloquea)
        for (var promotion : allPromotions) {
            if (!promotion.active() && !promotion.plannedAhead(today)) continue;
            materialNotice(promotion, missingMaterial.of(promotion.id())).ifPresent(all::add);
        }

        // 3. Marca sin todas sus carpetas
        for (var brand : brands) {
            if (promotions.hasAllBrandFolders(brand.id())) continue;
            all.add(recommendation("brand-folders:" + brand.id(), Recommendation.Kind.BRAND_FOLDERS_MISSING,
                    "Crea las carpetas de " + brand.name(), null,
                    Recommendation.Screen.BRAND_FOLDERS, brand.id(), null));
        }

        // 3a. Marca sin color: no genera fondos (ni SVG ni los de SocialHub) hasta que se elija
        for (var brand : promotions.brandsWithoutColor()) {
            all.add(recommendation("brand-color:" + brand.id(), Recommendation.Kind.BRAND_COLOR_MISSING,
                    "Elige el color de " + brand.name(),
                    "Sin color, SocialHub no genera fondos para sus piezas (ni los SVG de Claude ni los de SocialHub): las piezas sin imagen "
                            + "ni portada fallarán. El color es obligatorio al crear o editar una marca",
                    Recommendation.Screen.BRAND_DATA, brand.id(), null));
        }

        // 3b. Marca con promoción en marcha y sin tono o público: Claude propondría a ciegas
        for (var brand : brands) {
            if (allPromotions.stream().noneMatch(p -> p.brandId().equals(brand.id()) && (p.active() || p.plannedAhead(today)))) continue;
            var gaps = promotions.identityGaps(brand.id());
            if (gaps == null || gaps.isEmpty()) continue;
            all.add(recommendation("brand-identity:" + brand.id(), Recommendation.Kind.IDENTITY_MISSING,
                    "Cuéntale a Claude cómo es " + brand.name(),
                    "Falta " + String.join(", ", gaps) + ". Sin eso, Claude propone sin conocer la voz de la marca ni a quién se dirige. "
                            + "Puedes rellenarlo o importarlo desde un .txt",
                    Recommendation.Screen.BRAND_IDENTITY, brand.id(), null));
        }

        // 3c. Kit de marca: sin kit aprobado, la marca (con alguna promoción) lo pide; con uno pendiente, a revisarlo
        for (var brand : brands) {
            if (brandKits.approved(brand.id()).isPresent()) continue;
            if (brandKits.pending(brand.id()).isPresent()) {
                all.add(recommendation("brand-kit-pending:" + brand.id(), Recommendation.Kind.BRAND_KIT_PENDING,
                        "Revisa el kit de " + brand.name(),
                        "Claude ha dejado una propuesta de kit (paleta y plantillas). No se usa hasta que la apruebes",
                        Recommendation.Screen.BRAND_KIT, brand.id(), null));
                continue;
            }
            // Encargo de kit abierto en cualquier promoción de la marca (el mismo criterio que usa la API para no pedir otro)
            if (brandKits.openOrderPromotion(brand.id()).isPresent()) continue;
            if (allPromotions.stream().noneMatch(p -> p.brandId().equals(brand.id()))) continue;
            all.add(recommendation("brand-kit:" + brand.id(), Recommendation.Kind.BRAND_KIT_MISSING,
                    "Genera el kit de " + brand.name(),
                    "El kit es la paleta y las plantillas de sus piezas (cierre del carrusel, historia…), diseñadas una vez por Claude y aprobadas por ti",
                    Recommendation.Screen.BRAND_KIT, brand.id(), null));
        }

        // 4. Encargo sin recoger (los del PC los recoge otra tarea y no se miran)
        // Si la carpeta de «Encargos» de la marca no está disponible, no se sabe si Claude ha dejado el resultado
        sent.stream().filter(s -> !s.onPc() && s.ordersAvailable() && ClaudePickups.overdue(s.sentAt(), now, s.urgent()))
                .map(s -> ClaudePickups.pickupFor(s.sentAt(), s.urgent()))
                .max(Comparator.naturalOrder())
                .ifPresent(pickup -> all.add(recommendation("orders-not-collected",
                        Recommendation.Kind.ORDERS_NOT_COLLECTED,
                        "No ha llegado el resultado de los encargos de las " + hour(pickup) + ". Revisa la tarea programada",
                        null, null, null, null)));

        // 5. Marca sin cuenta de Publora
        for (var brand : brands) {
            if (publora.hasAccounts(brand.id()) || publora.isDismissed(brand.id())) continue;
            all.add(recommendation("publora:" + brand.id(), Recommendation.Kind.PUBLORA_MISSING,
                    "Conecta Publora para " + brand.name(), null,
                    Recommendation.Screen.PUBLORA, brand.id(), null));
        }

        // 6. Sin carpeta de copias
        if (!backups.hasChosenFolder()) {
            all.add(recommendation("backup-folder", Recommendation.Kind.BACKUP_FOLDER_MISSING,
                    "Elige dónde guardar las copias", null, Recommendation.Screen.BACKUPS, null, null));
        }

        // 7. Marca sin promoción activa
        for (var brand : brands) {
            var ofBrand = allPromotions.stream().filter(p -> p.brandId().equals(brand.id())).toList();
            if (ofBrand.stream().anyMatch(PromotionQueries.PromotionRef::active)) continue;
            // Una planificada que aún no ha terminado se activará sola el día que empiece: no hace falta pedirlo
            if (ofBrand.stream().anyMatch(p -> p.plannedAhead(today))) continue;
            // Con promociones planificadas o en pausa se pide activar una (y el botón la abre); si no tiene ninguna,
            // dar de alta la primera. Si solo tiene terminadas, hace falta una nueva.
            var toActivate = ofBrand.stream().filter(p -> "PAUSED".equals(p.status())).findFirst();
            if (toActivate.isPresent()) {
                all.add(recommendation("no-promotion:" + brand.id(), Recommendation.Kind.NO_ACTIVE_PROMOTION,
                        "Activa una promoción de " + brand.name(), null,
                        Recommendation.Screen.NEW_PROMOTION, brand.id(), toActivate.get().id()));
            } else {
                all.add(recommendation("no-promotion:" + brand.id(), Recommendation.Kind.NO_ACTIVE_PROMOTION,
                        ofBrand.isEmpty() ? "Da de alta la primera promoción de " + brand.name()
                                : "Da de alta una promoción nueva de " + brand.name(), null,
                        Recommendation.Screen.NEW_PROMOTION, brand.id(), null));
            }
        }

        // 8. Propuestas pedidas y en camino
        Map<String, AiSentOrders.Sent> waiting = new LinkedHashMap<>();
        for (var s : sent) {
            if (s.type() == AiRequestType.WEEKLY_PROPOSALS && s.promotionId() != null
                    && promotionById.containsKey(s.promotionId())) {
                waiting.putIfAbsent(s.promotionId(), s);
            }
        }
        waiting.forEach((promotionId, order) -> all.add(recommendation("proposals-pending:" + promotionId,
                Recommendation.Kind.PROPOSALS_PENDING,
                "Propuestas de «" + promotionById.get(promotionId).name() + "» pedidas. Llegarán "
                        + ClaudePickups.etaText(order.sentAt(), now, order.urgent()),
                null, null, null, null).promotionId(promotionId).etaText(ClaudePickups.etaText(order.sentAt(), now, order.urgent()))));

        // 9. Promociones que SocialHub ha activado solas hoy (informativa, sin botón)
        autoStarted.on(today).stream().sorted().filter(promotionById::containsKey).forEach(id -> all.add(
                recommendation("promotion-auto-started:" + id, Recommendation.Kind.PROMOTION_AUTO_STARTED,
                        "Promoción «" + promotionById.get(id).name() + "» activada sola: empieza hoy", null, null, null, null)
                        .promotionId(id)));

        return List.copyOf(all);
    }

    /** Cuántos títulos se nombran en el aviso de material; el resto va como «y N más». */
    static final int MAX_TITLES = 5;

    static java.util.Optional<Recommendation> materialNotice(PromotionQueries.PromotionRef promotion,
                                                             MissingMaterial.Report report) {
        if (report.pending() == 0 || (report.accessible() && report.missing().isEmpty())) return java.util.Optional.empty();
        String style = report.visualStyle() == null || report.visualStyle().isBlank() ? ""
                : " Claude diseñará los fondos con el estilo visual de la promoción: «" + report.visualStyle().strip() + "».";
        String fallback = report.cover() != null ? "la portada de la marca («" + report.cover() + "»)"
                : "un fondo con el color de la marca (o la portada de la marca si pones un archivo «portada.jpg», «portada.jpeg» o "
                        + "«portada.png» en la raíz de su carpeta Material)";
        String id = "material-missing:" + promotion.id();
        if (!report.accessible()) {
            return java.util.Optional.of(recommendation(id, Recommendation.Kind.MATERIAL_MISSING,
                    "No se puede comprobar el material de «" + promotion.name() + "»",
                    "La carpeta de Material no está disponible en este ordenador (¿está abierto Google Drive?). Es solo un aviso: "
                            + "lo que no tenga imagen ni vídeo usará, en este orden, el fondo SVG de Claude, " + fallback
                            + " o un fondo de SocialHub." + style,
                    Recommendation.Screen.BRAND_FOLDERS, promotion.brandId(), null).promotionId(promotion.id()));
        }
        var titles = report.missing();
        String names = String.join(", ", titles.stream().limit(MAX_TITLES).toList())
                + (titles.size() > MAX_TITLES ? " y " + (titles.size() - MAX_TITLES) + " más" : "");
        return java.util.Optional.of(recommendation(id, Recommendation.Kind.MATERIAL_MISSING,
                "Faltan imágenes o vídeos para: " + names,
                "Si Claude no entrega un fondo SVG, SocialHub usará " + fallback + ". No bloquea nada." + style,
                Recommendation.Screen.BRAND_FOLDERS, promotion.brandId(), null).promotionId(promotion.id()));
    }

    private static boolean isTikTok(PublishingQueries.WithoutMargin w) { return com.socialhub.settings.PublishSettings.TIKTOK.equals(w.platform()); }

    private static Recommendation duplicates(PublishingQueries.DuplicateGroup group,
                                             PromotionQueries.PromotionRef promotion) {
        String network = group.network();
        String day = group.day().getDayOfMonth() + " de " + group.day().getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
        boolean sameTitle = group.sameTitle();
        return recommendation("duplicates:" + group.publicationIds().getFirst() + ":" + (sameTitle ? "title" : "text"),
                Recommendation.Kind.DUPLICATE_PUBLICATIONS,
                "Posible duplicado: «" + group.title() + "» (" + group.publicationIds().size() + " publicaciones de " + network + ")",
                sameTitle ? "El " + day + ", con el mismo título y el mismo tipo. Descarta la que sobre"
                        : "El " + day + " a la misma hora y con el mismo texto. Descarta la que sobre",
                Recommendation.Screen.PUBLICATIONS, promotion.brandId(), promotion.id())
                .promotionId(promotion.id()).publicationIds(group.publicationIds());
    }

    private static Recommendation noMargin(PromotionQueries.PromotionRef promotion,
                                              List<PublishingQueries.WithoutMargin> list, Instant now, java.time.Duration margin,
                                              boolean tiktok) {
        var first = list.getFirst();
        var when = first.scheduledAt().atZone(ClaudePickups.ZONE);
        String day = when.getDayOfMonth() + " de " + when.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"));
        boolean past = !first.scheduledAt().isAfter(now);
        String span = com.socialhub.platform.SpanText.of(margin);
        String title = list.size() == 1 ? "«" + first.title() + "» ya no deja " + span + " de margen"
                : list.size() + (tiktok ? " publicaciones de TikTok de «" : " publicaciones de «") + promotion.name() + "» ya no dejan " + span + " de margen";
        String detail = (list.size() == 1 ? "" : "La primera, «" + first.title() + "»: ")
                + (past ? "su hora (el " + day + " a las " + hour(when) + ") ya pasó"
                        : "sale el " + day + " a las " + hour(when) + " (faltan menos de " + span + ")")
                + " y aún no está programada. SocialHub no programa con menos de " + span + " de margen (ajuste «" + (tiktok ? "Margen mínimo de publicación en TikTok" : "Margen mínimo de publicación") + "»): cambia la hora o publícala a mano";
        return recommendation("publication-too-soon:" + promotion.id() + (tiktok ? ":tiktok" : ""), Recommendation.Kind.PUBLICATION_TOO_SOON,
                title, detail, Recommendation.Screen.PUBLICATIONS, promotion.brandId(), promotion.id())
                .promotionId(promotion.id()).publicationIds(list.stream().map(PublishingQueries.WithoutMargin::publicationId).toList());
    }

    private static Recommendation heldRow(PromotionQueries.PromotionRef promotion, String reason,
                                             Recommendation.@Nullable Screen screen) {
        return recommendation("held-proposals:" + promotion.id(), Recommendation.Kind.HELD_PROPOSALS,
                "Las propuestas de «" + promotion.name() + "» no pueden salir", reason, screen,
                screen == Recommendation.Screen.BRAND_FOLDERS ? promotion.brandId() : null, null)
                .promotionId(promotion.id());
    }

    static final String PC_TASK_SILENT_TITLE = "La tarea «Publicar con Chrome» no contesta";

    private static Recommendation pcTaskSilent(ClaudeActivity.Silence silence, Instant now) {
        return recommendation("pc-task-silent", Recommendation.Kind.PC_TASK_SILENT, PC_TASK_SILENT_TITLE,
                pcDetail(silence, now), null, null, null);
    }

    static String pcDetail(ClaudeActivity.Silence silence, Instant now) {
        return silence.count() + (silence.count() == 1 ? " encargo sin contestar" : " encargos sin contestar") + " desde "
                + ClaudeActivity.since(silence.since(), now) + ". ¿Está activa la tarea «Publicar con Chrome» y abierto Chrome?";
    }

    private java.util.Optional<Recommendation> noPublishingRoute(PromotionQueries.BrandRef brand,
            Map<String, PromotionQueries.PromotionRef> promotionById, List<PublishingQueries.Unroutable> unroutable) {
        var ofBrand = unroutable.stream().filter(u -> {
            var promotion = promotionById.get(u.promotionId());
            return promotion != null && promotion.brandId().equals(brand.id());
        }).sorted(Comparator.comparing(PublishingQueries.Unroutable::scheduledOn)).toList();
        if (ofBrand.isEmpty()) return java.util.Optional.empty();
        var first = ofBrand.getFirst();
        boolean dismissed = first.gap() == PubloraConnection.Gap.DISMISSED;
        String why = switch (first.gap()) {
            case NO_KEY -> "Publora no tiene clave";
            case DISMISSED -> "Publora está marcada como «No uso Publora»";
            default -> "Publora no tiene cuenta de " + first.platform();
        };
        int n = ofBrand.size();
        String detail = n + (n == 1 ? " publicación" : " publicaciones") + " de los próximos 7 días (la primera, el "
                + first.scheduledOn().getDayOfMonth() + " de " + first.scheduledOn().getMonth().getDisplayName(TextStyle.SHORT, Locale.of("es", "ES"))
                + ") no tiene por dónde salir: la tarea «Publicar con Chrome» no contesta y " + why + ".";
        return java.util.Optional.of(recommendation("no-publishing-route:" + brand.id(), Recommendation.Kind.NO_PUBLISHING_ROUTE,
                "No hay forma de publicar " + brand.name(), detail,
                dismissed ? null : Recommendation.Screen.PUBLORA, dismissed ? null : brand.id(), null));
    }

    private static String hour(ZonedDateTime time) { return ClaudePickups.hour(time); }

    private static Recommendation instagramExpiring(InstagramOAuth.Notice n) {
        String title = n.expired() ? "La conexión con Instagram ha caducado"
                : n.daysLeft() == 0 ? "La conexión con Instagram caduca hoy"
                : n.daysLeft() == 1 ? "La conexión con Instagram caduca mañana"
                : "La conexión con Instagram caduca en " + n.daysLeft() + " días";
        String detail = n.systemKeyFallback()
                ? "Mientras no la renueves se usa la clave de usuario del sistema. Pulsa «Renovar conexión» (un clic)"
                : n.expired() ? "Sin ella no se puede publicar por la API oficial: pulsa «Renovar conexión» (un clic)"
                : "Pulsa «Renovar conexión» (un clic) antes de que caduque";
        return recommendation("instagram-connection-expiring", Recommendation.Kind.INSTAGRAM_CONNECTION_EXPIRING, title,
                detail, Recommendation.Screen.INSTAGRAM, null, null);
    }

    private static Recommendation recommendation(String id, Recommendation.Kind kind, String title,
                                                 @Nullable String detail,
                                                 Recommendation.@Nullable Screen screen,
                                                 @Nullable String brandId, @Nullable String promotionId) {
        var recommendation = new Recommendation(id, kind, title);
        if (detail != null && !detail.isBlank()) recommendation = recommendation.detail(detail);
        if (screen != null) {
            recommendation = new Recommendation(id, kind, title, recommendation.detail(), null, null, null,
                    new Recommendation.Target(screen, brandId, promotionId));
        }
        return recommendation;
    }
}
