package com.socialhub.ai.application;

import com.socialhub.ai.AiSentOrders;
import com.socialhub.ai.domain.AiRequest;
import com.socialhub.ai.domain.AiRequestRepository;
import com.socialhub.production.ProductionAiTasks;
import com.socialhub.promotion.PromotionQueries;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AiSentOrdersService implements AiSentOrders {

    private final AiRequestRepository requests;
    private final ProductionAiTasks production;
    private final PromotionQueries promotions;

    @Override
    @Transactional(readOnly = true)
    public List<Sent> waiting() {
        return requests.withStatus(AiRequest.Status.SENT).stream()
                .filter(r -> production.waitingAiTask(r.taskId()).isPresent())
                .map(r -> {
                    boolean available = promotions.brandOrdersFolder(r.brandId()).map(AiFolders::available).orElse(false);
                    return new Sent(r.promotionId(), r.type(), r.sentAt(), r.type().onPc(), available, r.urgent());
                })
                .sorted(Comparator.comparing(Sent::sentAt))
                .toList();
    }
}
