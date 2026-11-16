package com.socialhub.publishing;

import com.socialhub.publishing.domain.QualityIssue;
import com.socialhub.publishing.domain.QualityRepository;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** El control de calidad de una publicación: avisos abiertos, aceptados y resultado de la revisión. */
@PrimaryPort
public interface PublicationQuality {

    List<QualityIssue> issues(String publicationId);

    long openIssues(String publicationId);

    /** Raúl acepta un aviso concreto: deja de bloquear. El motivo queda anotado. */
    List<QualityIssue> accept(String publicationId, String key, String reason);

    /**
     * Cambió el texto o los archivos de la publicación: lo aceptado antes ya no vale. Los avisos vuelven a comprobarse
     * y reaparecen si siguen fallando. Aceptar solo vale mientras no cambie la publicación.
     */
    void invalidateAcceptances(String publicationId);

    /** Guarda los avisos de la revisión de Claude (sustituyen a los de su revisión anterior). */
    void applyReview(String publicationId, List<QualityRepository.ReviewIssue> review, String taskId);
}
