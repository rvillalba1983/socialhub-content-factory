package com.socialhub.publishing;

import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Publication;
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
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de calidad de las publicaciones: avisos del código (en vivo) y de la última revisión de Claude, con las
 * aceptaciones de Raúl. Mientras quede un aviso abierto, la publicación no pasa a «Aprobada» ni a «Programada».
 */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class QualityService {

    private final PublicationRepository publications;
    private final PublicationFileRepository files;
    private final QualityRepository quality;
    private final PromotionQueries promotions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<QualityIssue> issues(String publicationId) {
        Publication p = find(publicationId);
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
        quality.reviewIssues(publicationId).forEach(r ->
                result.add(withAcceptance(new QualityIssue(r.key(), QualityIssue.Source.CLAUDE, r.message(), null, null), acceptances)));
        return result;
    }

    @Transactional(readOnly = true)
    public long openIssues(String publicationId) {
        return issues(publicationId).stream().filter(QualityIssue::open).count();
    }

    /** Raúl acepta un aviso concreto: deja de bloquear. El motivo queda anotado. */
    @Transactional
    public List<QualityIssue> accept(String publicationId, String key, String reason) {
        if (reason == null || reason.strip().length() < 3) throw new IllegalArgumentException("Indica por qué aceptas el aviso");
        if (issues(publicationId).stream().noneMatch(i -> i.key().equals(key))) {
            throw new IllegalArgumentException("Ese aviso ya no está en la publicación");
        }
        quality.accept(publicationId, key, reason.strip(), clock.instant());
        events.publishEvent(new FactoryChanged("publication", publicationId));
        return issues(publicationId);
    }

    /** Guarda los avisos de la revisión de Claude (sustituyen a los de su revisión anterior). */
    @Transactional
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
