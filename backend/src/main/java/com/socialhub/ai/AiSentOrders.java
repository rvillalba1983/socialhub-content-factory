package com.socialhub.ai;

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

    /** Un encargo enviado y sin resultado. {@code onPc}: lo hace Claude con Chrome en el PC (otra carpeta y otra tarea). */
    public record Sent(@Nullable String promotionId, AiRequestType type, Instant sentAt, boolean onPc) {}

    private final AiRequestRepository requests;

    /** Los encargos enviados y sin resultado, del más antiguo al más nuevo. */
    @Transactional(readOnly = true)
    public List<Sent> waiting() {
        return requests.findByStatus(AiRequestEntity.Status.SENT).stream()
                .map(r -> {
                    AiRequestType type = AiRequestType.valueOf(r.getType());
                    return new Sent(r.getPromotionId(), type, r.getSentAt(), type.onPc());
                })
                .sorted(java.util.Comparator.comparing(Sent::sentAt))
                .toList();
    }
}
