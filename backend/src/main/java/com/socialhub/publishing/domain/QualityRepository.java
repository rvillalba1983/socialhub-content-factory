package com.socialhub.publishing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Aceptaciones de avisos y avisos de la revisión de Claude. */
@SecondaryPort
public interface QualityRepository {

    record Acceptance(String reason, Instant at) {}

    record ReviewIssue(String key, String message) {}

    Map<String, Acceptance> acceptances(String publicationId);

    void accept(String publicationId, String key, String reason, Instant at);

    List<ReviewIssue> reviewIssues(String publicationId);

    /** Sustituye los avisos de Claude de la publicación por los de su última revisión. */
    void replaceReviewIssues(String publicationId, List<ReviewIssue> issues, String taskId, Instant at);
}
