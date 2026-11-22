package com.socialhub.publishing.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Citas declaradas por Claude en cada publicación. */
@SecondaryPort
public interface CitationRepository {

    List<Citation> citations(String publicationId);

    /** Sustituye las citas de la publicación por las nuevas (vacío las quita). */
    void replace(String publicationId, List<Citation> citations);
}
