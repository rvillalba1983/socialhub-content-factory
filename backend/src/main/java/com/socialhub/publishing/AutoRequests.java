package com.socialhub.publishing;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.AiRequestType;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.publishing.domain.AutoRequestRepository;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Autonomía (fase 8, bloque 2): SocialHub pide solo a Claude
 * <ul>
 *   <li>los textos de las publicaciones en producción que no tienen texto (salvo historias), uno por promoción;</li>
 *   <li>la revisión de calidad de las que pasan a «Pendiente de revisión», si Claude no ha revisado ya ese texto.</li>
 * </ul>
 * Cada encargo se apunta para no repetirlo. Cuentan en el límite diario como los demás.
 */
@Slf4j
@Component
class AutoRequests {

    static final String CAPTIONS = "CAPTIONS";
    static final String REVIEW = "QUALITY_REVIEW";
    static final int MAX_PER_ORDER = 10;
    private static final Set<PublicationStatus> IN_PRODUCTION = Set.of(PublicationStatus.IN_PRODUCTION, PublicationStatus.DRAFT);

    private final PublicationRepository publications;
    private final AutoRequestRepository done;
    private final AiOrders orders;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AutoRequests(PublicationRepository publications, AutoRequestRepository done, @Lazy AiOrders orders,
                 TransactionTemplate transactions, Clock clock) {
        this.publications = publications;
        this.done = done;
        this.orders = orders;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${socialhub.auto.initial-delay-millis:150000}",
               fixedDelayString = "${socialhub.auto.tick-millis:300000}")
    void run() {
        AppModeHolder.runIn(AppMode.REAL, () -> {
            try {
                requestDue();
            } catch (RuntimeException e) {
                log.warn("Encargos automáticos omitidos: {}", e.toString());
            }
        });
    }

    /** @return cuántos encargos se han pedido */
    int requestDue() {
        int n = 0;
        Map<String, List<Publication>> withoutText = new LinkedHashMap<>();
        List<Publication> toReview = new ArrayList<>();
        for (Publication p : publications.findAll()) {
            if (p.isDemo()) continue;
            boolean story = QualityChecker.kind(p.getType()) == QualityChecker.Kind.STORY;
            if (IN_PRODUCTION.contains(p.getStatus()) && !story && p.getCaption() == null
                    && !done.exists(p.getId(), CAPTIONS, "")) {
                withoutText.computeIfAbsent(p.getPromotionId(), k -> new ArrayList<>()).add(p);
            }
            if (p.getStatus() == PublicationStatus.IN_REVIEW && (story || p.getCaption() != null)
                    && !done.exists(p.getId(), REVIEW, fingerprint(p))) {
                toReview.add(p);
            }
        }
        for (var entry : withoutText.entrySet()) {
            List<Publication> all = entry.getValue();
            for (int from = 0; from < all.size(); from += MAX_PER_ORDER) {
                List<Publication> batch = all.subList(from, Math.min(all.size(), from + MAX_PER_ORDER));
                if (submit(entry.getKey(), batch)) n++;
            }
        }
        for (Publication p : toReview) {
            if (review(p)) n++;
        }
        return n;
    }

    private boolean submit(String promotionId, List<Publication> batch) {
        try {
            transactions.executeWithoutResult(s -> {
                List<String> ids = batch.stream().map(Publication::getId).toList();
                String task = orders.submit(promotionId, AiRequestType.CAPTIONS,
                        "Textos de " + ids.size() + (ids.size() == 1 ? " publicación" : " publicaciones") + " en producción",
                        "Escribe el texto y los hashtags de estas publicaciones en producción que aún no tienen texto, "
                                + "con la identidad y la memoria editorial de la marca.", ids);
                ids.forEach(id -> done.add(id, CAPTIONS, "", task, clock.instant()));
            });
            log.info("Textos pedidos solos para {} publicaciones de {}", batch.size(), promotionId);
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudieron pedir los textos de {}: {}", promotionId, e.getMessage());
            return false;
        }
    }

    private boolean review(Publication p) {
        try {
            transactions.executeWithoutResult(s -> {
                String task = orders.submit(p.getPromotionId(), AiRequestType.QUALITY_REVIEW, "Revisión · " + p.getTitle(),
                        "Revisa la calidad de «" + p.getTitle() + "»: coherencia, tono de la marca, ortografía y afirmaciones sin fuente.",
                        List.of(p.getId()));
                done.add(p.getId(), REVIEW, fingerprint(p), task, clock.instant());
            });
            log.info("Revisión de calidad pedida sola para {}", p.getId());
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo pedir la revisión de {}: {}", p.getId(), e.getMessage());
            return false;
        }
    }

    /** Versión del texto: si Raúl lo cambia y vuelve a «Pendiente de revisión», se revisa de nuevo. */
    static String fingerprint(Publication p) {
        String text = p.getTitle() + "\n" + (p.getCaption() == null ? "" : p.getCaption()) + "\n"
                + (p.getHashtags() == null ? "" : p.getHashtags());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
