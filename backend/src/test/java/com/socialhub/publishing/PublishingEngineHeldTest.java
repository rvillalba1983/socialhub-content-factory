package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiOrders;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublicationStatus;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Retención en el motor: no pisa otra incidencia, se quita al sacarla de «Programada» y no avisa si nada cambia. */
class PublishingEngineHeldTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final Instant NOW = Instant.now();

    private final PublishJobRepository jobs = mock(PublishJobRepository.class);
    private final PublicationRepository publications = mock(PublicationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private String reason = "Falta la cuenta de Publer de Litoques para TikTok";

    private PublishingEngine engine() {
        PublishRoute route = new PublishRoute() {
            @Override public Id id() { return Id.PUBLER; }
            @Override public boolean configured() { return true; }
            @Override public Instant lastMoment(Instant scheduledAt) { return scheduledAt.minus(Duration.ofHours(7)); }
            @Override public Outcome schedule(Publication p, PublishJob job, Instant now) { return new Outcome.Held(reason); }
        };
        return new PublishingEngine(jobs, publications, mock(PublicationFileRepository.class), mock(PromotionQueries.class),
                List.of(route), events, transactions, mock(AiOrders.class), Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private Publication publication(String flag) {
        return Publication.builder().id("p1").promotionId("promo").platform(Platform.TIKTOK).type("Vídeo").title("T")
                .scheduledOn(LocalDate.now(MADRID).plusDays(20)).scheduledTime(LocalTime.of(19, 0))
                .status(PublicationStatus.SCHEDULED).flag(flag).build();
    }

    @BeforeEach
    void saves() {
        org.mockito.Mockito.doAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null))
                .when(transactions).execute(any());
        when(jobs.find("p1")).thenReturn(Optional.empty());
    }

    @Test
    void theHeldFlagIsWrittenOnlyIfThePublicationHasNoOtherIncident() {
        var free = publication(null);
        engine().start(free);
        assertThat(free.getFlag()).isEqualTo("Retenida: " + reason);

        var other = publication("Otra incidencia");
        engine().start(other);
        assertThat(other.getFlag()).isEqualTo("Otra incidencia");
        // El motivo queda en la tarea
        var saved = org.mockito.ArgumentCaptor.forClass(PublishJob.class);
        verify(jobs, org.mockito.Mockito.times(2)).save(saved.capture());
        var job = saved.getAllValues().get(1);
        assertThat(job.getStatus()).isEqualTo(PublishJob.Status.HELD);
        assertThat(job.getLastError()).isEqualTo(reason);
        assertThat(job.getLog().getLast()).contains("retenida").contains(reason);
    }

    @Test
    void takingItOutOfScheduledCancelsTheHeldJobAndRemovesItsFlag() {
        var p = publication(null);
        var engine = engine();
        engine.start(p);
        assertThat(p.getFlag()).startsWith("Retenida: ");
        var held = new PublishJob("p1", PublishJob.Status.HELD, 0, "Publer", null, List.of(), null, null, NOW, 1, reason, null,
                null, null, List.of(), NOW, NOW, 0);
        when(jobs.find("p1")).thenReturn(Optional.of(held));

        engine.stop(p, "Raúl la ha sacado de «Programada»");

        assertThat(held.getStatus()).isEqualTo(PublishJob.Status.CANCELLED);
        assertThat(p.getFlag()).isNull();
    }

    @Test
    void stillHeldForTheSameReasonSavesNothingAndDoesNotNotify() {
        var p = publication(null);
        var held = new PublishJob("p1", PublishJob.Status.HELD, 0, "Publer", null, List.of(), null, null, NOW.minusSeconds(60), 1,
                reason, null, null, null, List.of(), NOW.minusSeconds(600), NOW.minusSeconds(300), 0);
        when(jobs.find("p1")).thenReturn(Optional.of(held));
        when(publications.findById("p1")).thenReturn(Optional.of(p));
        when(jobs.withStatus(any())).thenReturn(List.of(held));
        var engine = engine();

        assertThat(engine.tick()).isZero();
        verify(jobs, never()).save(any());
        verify(publications, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));

        // Si el motivo cambia (por ejemplo, ya hay cuenta pero el plan está lleno), sí se guarda y se avisa
        reason = "La cuenta «X» de Publer ya tiene 10 publicaciones programadas";
        var again = new PublishJob("p1", PublishJob.Status.HELD, 0, "Publer", null, List.of(), null, null, NOW.minusSeconds(60), 1,
                "Falta la cuenta de Publer de Litoques para TikTok", null, null, null, List.of(), NOW.minusSeconds(600), NOW.minusSeconds(300), 0);
        when(jobs.find("p1")).thenReturn(Optional.of(again));
        when(jobs.withStatus(any())).thenReturn(List.of(again));
        assertThat(engine.tick()).isEqualTo(1);
        verify(jobs).save(again);
        verify(events).publishEvent(any(Object.class));
    }
}
