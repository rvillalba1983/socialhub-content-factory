package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.QualityRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaQualityRepository implements QualityRepository {

    private final QualityAckJpaRepository acks;
    private final ReviewIssueJpaRepository reviews;

    @Override
    public Map<String, Acceptance> acceptances(String publicationId) {
        Map<String, Acceptance> map = new LinkedHashMap<>();
        acks.findByPublicationId(publicationId).forEach(a -> map.put(a.getIssueKey(), new Acceptance(a.getReason(), a.getAcceptedAt())));
        return map;
    }

    @Override
    public void accept(String publicationId, String key, String reason, Instant at) {
        acks.save(new QualityEntities.AckEntity(publicationId, key, reason, at));
    }

    @Override
    public List<ReviewIssue> reviewIssues(String publicationId) {
        return reviews.findByPublicationIdOrderByIssueKey(publicationId).stream()
                .map(r -> new ReviewIssue(r.getIssueKey(), r.getMessage())).toList();
    }

    @Override
    public void replaceReviewIssues(String publicationId, List<ReviewIssue> issues, String taskId, Instant at) {
        reviews.deleteByPublication(publicationId);
        issues.forEach(i -> reviews.save(new QualityEntities.ReviewIssueEntity(publicationId, i.key(), i.message(), taskId, at)));
    }
}
