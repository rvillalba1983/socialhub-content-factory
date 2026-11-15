package com.socialhub.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.production.domain.Agent;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskCheck;
import com.socialhub.production.domain.TaskCommandType;
import com.socialhub.production.domain.TaskRepository;
import com.socialhub.production.domain.TaskStatus;
import com.socialhub.production.domain.events.ProductionEvents.ReviewApproved;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;

/** Módulo de producción por separado: al aprobar una revisión, publica el evento que atiende publicaciones. */
@ApplicationModuleTest
@org.springframework.test.context.TestPropertySource(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.default-mode=REAL"
})
@org.springframework.context.annotation.Import(com.socialhub.PinnedClock.class)
class ProductionModuleTest {

    @Autowired ProductionService service;
    @Autowired ProductionQueries queries;
    @Autowired TaskRepository tasks;

    @Test
    void approvingAReviewPublishesReviewApproved(AssertablePublishedEvents events) {
        String station = queries.stations().getFirst().getId();
        tasks.save(Task.builder().id("review-1").stationId(station).type(Task.TYPE_REVIEW)
                .title("Revisar").status(TaskStatus.WAITING).agent(Agent.raul()).human(true).attempts(1).maxAttempts(2)
                .checks(List.of(new TaskCheck("Comprobación", false))).build());

        service.execute("review-1", TaskCommandType.APPROVE, null);

        assertThat(events).contains(ReviewApproved.class)
                .matching(ReviewApproved::taskId, "review-1");
    }
}
