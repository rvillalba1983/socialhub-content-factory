package com.socialhub.publishing.application;

import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublicationQuality;
import com.socialhub.publishing.domain.Citation;
import com.socialhub.publishing.domain.CitationRepository;
import com.socialhub.publishing.domain.DuplicateDetector;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.TextLinks;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.QualityIssue;
import com.socialhub.publishing.domain.QualityRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de calidad de las publicaciones: avisos del código (en vivo) y de la última revisión de Claude, con las
 * aceptaciones de Raúl. Mientras quede un aviso abierto, la publicación no pasa a «Aprobada» ni a «Programada».
 */
@Service
@RequiredArgsConstructor
class QualityService implements PublicationQuality {

    private final PublicationRepository publications;
    private final PublicationFileRepository files;
    private final QualityRepository quality;
    private final CitationRepository citations;
    private final CitationChecks citationChecks;
    private final LinkChecks linkChecks;
    private final PromotionQueries promotions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Todos los avisos: los que bloquean (código, citas y revisión de Claude) y los informativos (enlaces, texto ya usado). */
    @Transactional(readOnly = true)
    @Override
    public List<QualityIssue> issues(String publicationId) {
        Publication p = find(publicationId);
        List<QualityIssue> result = blocking(p);
        result.addAll(informative(p));
        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public long openIssues(String publicationId) {
        return blocking(find(publicationId)).stream().filter(QualityIssue::open).count();
    }

    private List<QualityIssue> blocking(Publication p) {
        String publicationId = p.getId();
        Map<String, String> others = new LinkedHashMap<>();
        publications.findAll().stream()
                .filter(o -> o.getPromotionId().equals(p.getPromotionId()) && !o.getId().equals(p.getId())
                        && o.getStatus() != PublicationStatus.CANCELLED)
                .forEach(o -> others.put(o.getId(), o.getCaption()));
        List<String> paths = files.files(publicationId).stream().map(PublicationFile::path).toList();
        var acceptances = quality.acceptances(publicationId);
        List<QualityIssue> result = new ArrayList<>();
        QualityChecker.check(p, paths, promotions.qualityRulesOf(p.getPromotionId()), others).forEach((key, message) ->
                result.add(withAcceptance(new QualityIssue(key, QualityIssue.Source.CODE, message, null, null), acceptances)));
        // Citas: tienen que aparecer literalmente en un documento de la promoción (las verificadas solo informan)
        List<Citation> declared = citations.citations(publicationId);
        if (!declared.isEmpty()) {
            citationChecks.check(p.getPromotionId(), declared).stream().filter(r -> !r.ok()).forEach(r ->
                    result.add(withAcceptance(new QualityIssue(r.key(), QualityIssue.Source.CODE, r.message(), null, null), acceptances)));
        }
        quality.reviewIssues(publicationId).forEach(r ->
                result.add(withAcceptance(new QualityIssue(r.key(), QualityIssue.Source.CLAUDE, r.message(), null, null), acceptances)));
        return result;
    }

    /** Avisos que solo informan y nunca bloquean: citas verificadas, estado de los enlaces y texto ya usado por la marca. */
    private List<QualityIssue> informative(Publication p) {
        List<QualityIssue> info = new ArrayList<>();
        List<Citation> declared = citations.citations(p.getId());
        if (!declared.isEmpty()) {
            citationChecks.check(p.getPromotionId(), declared).stream().filter(CitationChecks.Result::ok)
                    .forEach(r -> info.add(QualityIssue.info(r.key(), r.message())));
        }
        info.addAll(linkIssues(p));
        info.addAll(reusedText(p));
        return info;
    }

    /** «Enlace OK», «No responde» o «No comprobado» para cada enlace del texto. Lo que no se sabe aún se pide en segundo plano. */
    private List<QualityIssue> linkIssues(Publication p) {
        List<QualityIssue> list = new ArrayList<>();
        List<String> links = TextLinks.find(p.getCaption());
        for (int i = 0; i < links.size(); i++) {
            String url = links.get(i);
            String key = "LINK:" + url;
            if (i >= TextLinks.MAX_LINKS) {
                list.add(QualityIssue.info(key, "No comprobado: " + url + " (solo se comprueban los " + TextLinks.MAX_LINKS + " primeros enlaces)."));
            } else if (!TextLinks.https(url)) {
                list.add(QualityIssue.info(key, "No comprobado: " + url + " (solo se comprueban enlaces https)."));
            } else {
                var known = linkChecks.known(url);
                if (known.isEmpty()) {
                    linkChecks.request(url, () -> events.publishEvent(new FactoryChanged("publication", p.getId())));
                    list.add(QualityIssue.info(key, "No comprobado: " + url + " (comprobándolo; vuelve a mirar en unos segundos)."));
                } else {
                    list.add(QualityIssue.info(key, switch (known.get()) {
                        case OK -> "Enlace OK: " + url;
                        case NOT_RESPONDING -> "No responde: " + url;
                        case REFUSED -> "No comprobado: " + url + " (SocialHub no comprueba direcciones privadas ni locales).";
                    }));
                }
            }
        }
        return list;
    }

    /** «Este texto ya se usó el …»: la misma marca tuvo exactamente ese texto en otra promoción. Solo informa. */
    private List<QualityIssue> reusedText(Publication p) {
        if (p.getCaption() == null || p.getCaption().isBlank()) return List.of();
        String mine = DuplicateDetector.normalize(p.getCaption());
        String brand = promotions.brandOf(p.getPromotionId());
        java.util.Set<String> sameBrand = new java.util.HashSet<>(promotions.promotionIdsOf(brand));
        sameBrand.remove(p.getPromotionId()); // dentro de la misma promoción ya avisa «DUPLICATE»
        List<QualityIssue> list = new ArrayList<>();
        publications.findAll().stream()
                .filter(o -> sameBrand.contains(o.getPromotionId()) && o.getStatus() != PublicationStatus.CANCELLED
                        && o.getCaption() != null && DuplicateDetector.normalize(o.getCaption()).equals(mine))
                .sorted(java.util.Comparator.comparing(Publication::getScheduledOn))
                .limit(3)
                .forEach(o -> list.add(QualityIssue.info("REUSED:" + o.getId(),
                        "Este texto ya se usó el " + DATE.format(o.getScheduledOn()) + " (publicación " + o.getId() + ", «" + o.getTitle() + "»).")));
        return list;
    }

    private static final java.time.format.DateTimeFormatter DATE =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es-ES"));

    /** Raúl acepta un aviso concreto: deja de bloquear. El motivo queda anotado. */
    @Transactional
    @Override
    public List<QualityIssue> accept(String publicationId, String key, String reason) {
        if (reason == null || reason.strip().length() < 3) throw new IllegalArgumentException("Indica por qué aceptas el aviso");
        if (blocking(find(publicationId)).stream().noneMatch(i -> i.key().equals(key))) {
            throw new IllegalArgumentException("Ese aviso ya no está en la publicación");
        }
        quality.accept(publicationId, key, reason.strip(), clock.instant());
        events.publishEvent(new FactoryChanged("publication", publicationId));
        return issues(publicationId);
    }

    /**
     * Cambió el texto o los archivos de la publicación: lo aceptado antes ya no vale. Los avisos vuelven a comprobarse
     * y reaparecen si siguen fallando. Aceptar solo vale mientras no cambie la publicación.
     */
    @Transactional
    @Override
    public void invalidateAcceptances(String publicationId) {
        quality.clearAcceptances(publicationId);
    }

    /** Guarda los avisos de la revisión de Claude (sustituyen a los de su revisión anterior). */
    @Transactional
    @Override
    public void applyReview(String publicationId, List<QualityRepository.ReviewIssue> review, String taskId) {
        find(publicationId);
        quality.replaceReviewIssues(publicationId, review, taskId, clock.instant());
        events.publishEvent(new FactoryChanged("publication", publicationId));
    }

    private static QualityIssue withAcceptance(QualityIssue issue, Map<String, QualityRepository.Acceptance> acceptances) {
        var a = acceptances.get(issue.key());
        return a == null ? issue : issue.accepted(a.reason(), a.at());
    }

    private Publication find(String id) {
        return publications.findById(id).orElseThrow(() -> new NotFoundException("No existe la publicación " + id));
    }
}
