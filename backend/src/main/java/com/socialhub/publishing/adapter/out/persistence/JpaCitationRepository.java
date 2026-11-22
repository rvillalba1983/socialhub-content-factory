package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Citation;
import com.socialhub.publishing.domain.CitationRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaCitationRepository implements CitationRepository {

    private final CitationJpaRepository citations;

    @Override
    public List<Citation> citations(String publicationId) {
        return citations.findByPublicationIdOrderByPosition(publicationId).stream()
                .map(c -> new Citation(c.getText(), c.getSourceFile(), c.getLocation())).toList();
    }

    @Override
    public void replace(String publicationId, List<Citation> list) {
        citations.deleteByPublication(publicationId);
        List<CitationEntity> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Citation c = list.get(i);
            rows.add(new CitationEntity(publicationId, i, c.text(), c.sourceFile(), c.location()));
        }
        citations.saveAll(rows);
    }
}
