package com.socialhub.publishing;

import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublishJob;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** La API oficial llega en el bloque 3 de la fase 7: hasta entonces figura como «no configurada». */
@Configuration
class UnconfiguredRoutes {

    @Bean @Order(3)
    PublishRoute officialRoute() { return new Unconfigured(PublishRoute.Id.OFFICIAL); }

    record Unconfigured(Id id) implements PublishRoute {
        @Override public boolean configured() { return false; }
        @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(Publication.MIN_MARGIN); }
        @Override public Outcome schedule(Publication publication, PublishJob job, Instant now) {
            return new Outcome.Failed("no configurada");
        }
    }
}
