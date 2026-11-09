package com.socialhub.ai;

import com.socialhub.production.ProductionService;
import com.socialhub.promotion.PromotionQueries;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Encargos a Claude que ya están en Drive y esperan a que los recoja. */
@Component
@RequiredArgsConstructor
public class AiSentOrders {

    /**
     * Un encargo enviado y sin resultado. {@code onPc}: lo hace Claude con Chrome en el PC (otra carpeta y otra tarea).
     * {@code ordersAvailable}: la carpeta de «Encargos» de su marca está disponible ahora en este ordenador; si no lo
     * está, no se sabe si Claude ha dejado su resultado.
     */
    public record Sent(@Nullable String promotionId, AiRequestType type, Instant sentAt, boolean onPc,
                       boolean ordersAvailable) {}

    private final AiRequestRepository requests;
    private final ProductionService production;
    private final PromotionQueries promotions;

    /**
     * Los encargos enviados cuya tarea sigue esperando a Claude, del más antiguo al más nuevo. Si la tarea se ha
     * cancelado o ya no espera, el encargo no cuenta aunque su petición siga como enviada.
     */
    @Transactional(readOnly = true)
    public List<Sent> waiting() {
        return requests.findByStatus(AiRequestEntity.Status.SENT).stream()
                .filter(r -> production.waitingAiTask(r.getTaskId()).isPresent())
                .map(r -> {
                    AiRequestType type = AiRequestType.valueOf(r.getType());
                    boolean available = promotions.brandOrdersFolder(r.getBrandId()).map(AiFolders::available).orElse(false);
                    return new Sent(r.getPromotionId(), type, r.getSentAt(), type.onPc(), available);
                })
                .sorted(java.util.Comparator.comparing(Sent::sentAt))
                .toList();
    }
}
