package com.socialhub.pieces.application;

import com.socialhub.pieces.ContentGaps;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ContentGapsService implements ContentGaps {

    private final WeeklySpecHandler specs;
    private final Clock clock;

    @Override
    public List<Gap> of(String promotionId) {
        return specs.gaps(promotionId, clock.instant()).stream()
                .map(g -> new Gap(g.publicationId(), g.title(), g.scheduledAt())).toList();
    }
}
