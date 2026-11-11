package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.ai.AiJob;
import com.socialhub.ai.AiRequestType;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.PublicationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WeeklyProposalsHandlerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PromotionQueries promotions = mock(PromotionQueries.class);
    private final PublishingService service = mock(PublishingService.class);

    private static Instant madrid(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(MADRID).toInstant();
    }

    private WeeklyProposalsHandler at(Instant now) {
        return new WeeklyProposalsHandler(promotions, mock(PublishingQueries.class), service, Clock.fixed(now, MADRID),
                mock(ExternalCalendar.class));
    }

    private static AiJob job(List<String> inputs, Instant created) {
        return new AiJob("t1", AiRequestType.WEEKLY_PROPOSALS, "lit-1", "litoques", "Propuestas", "goal", inputs, created);
    }

    private static JsonNode proposals(String... items) {
        return JSON.readTree("{\"proposals\":[" + String.join(",", items) + "]}");
    }

    private static String p(String day, String time, String platform) {
        return "{\"scheduled_on\":\"" + day + "\",\"scheduled_time\":\"" + time + "\",\"platform\":\"" + platform
                + "\",\"type\":\"Reel\",\"title\":\"Una idea\"}";
    }

    /** Lunes 9 de nov, 10:00; semana del 9 al 15; la promoción va del 3 de nov al 13 de dic. */
    private WeeklyProposalsHandler monday() {
        when(promotions.datesOf("lit-1")).thenReturn(new PromotionQueries.Dates(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 13)));
        return at(madrid(9, 10, 0));
    }

    private AiJob week9() { return job(List.of("2026-11-09"), madrid(8, 20, 42)); }

    @Test
    void anOrderWithoutDateStoresTheComingMondayWhenItIsCreated() {
        // Domingo 8 de nov a las 20:42
        var handler = at(madrid(8, 20, 42));
        assertThat(handler.completeInputs(List.of())).containsExactly("2026-11-09");
        assertThat(handler.completeInputs(List.of("2026-11-11"))).containsExactly("2026-11-11");
        assertThat(handler.completeInputs(List.of("algo"))).containsExactly("2026-11-09", "algo");
    }

    @Test
    void anOldOrderWithoutDateKeepsTheWeekOfItsCreationWhenTheResultIsReadOnMonday() {
        // Creado el domingo 8 a las 20:42, sin fecha; el resultado se lee el lunes 9: sigue siendo la semana del 9
        var handler = monday();
        AiJob old = job(List.of(), madrid(8, 20, 42));
        assertThat(handler.weekStart(old)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(handler.inputs(old)).containsEntry("week", java.util.Map.of("from", "2026-11-09", "to", "2026-11-15"));
        String text = handler.apply(old, proposals(p("2026-11-10", "19:00", "INSTAGRAM"), p("2026-11-12", "13:00", "tiktok")), JSON.createObjectNode());
        assertThat(text).startsWith("2 propuestas para revisar en Publicaciones").doesNotContain("descartada");
        verify(service, times(2)).create(eq("lit-1"), any(), eq(PublicationStatus.PROPOSAL));
    }

    @Test
    void aBadProposalIsDroppedAndTheGoodOnesGoIn() {
        var handler = monday();
        String text = handler.apply(week9(), proposals(
                p("2026-11-10", "19:00", "INSTAGRAM"),
                p("2026-11-09", "11:00", "INSTAGRAM"),   // a menos de 6 h (ahora, 10:00)
                p("2026-11-08", "19:00", "INSTAGRAM"),   // fecha pasada (y fuera de la semana)
                p("2026-11-16", "19:00", "INSTAGRAM"),   // fuera de la semana
                p("2026-11-11", "19:00", "FACEBOOK"),    // red no válida
                p("2026-11-11", "25:00", "TIKTOK"),      // hora ilegible
                "{\"scheduled_on\":\"2026-11-11\",\"scheduled_time\":\"19:00\",\"platform\":\"TIKTOK\",\"type\":\"\",\"title\":\"x\"}",
                p("2026-11-14", "10:00", "TIKTOK")), JSON.createObjectNode());
        assertThat(text).startsWith("2 propuestas para revisar en Publicaciones · 6 descartadas: ")
                .contains("3 con fecha, hora, red, tipo o título no válidos")
                .contains("2 fuera de la semana")
                .contains("1 con fecha pasada o a menos de 6 h");
        verify(service, times(2)).create(eq("lit-1"), any(), eq(PublicationStatus.PROPOSAL));
    }

    @Test
    void sixHoursIsTheMinimumLead() {
        var handler = monday();
        // Exactamente a las 16:00 (ahora + 6 h) entra; un minuto antes no
        assertThat(handler.apply(week9(), proposals(p("2026-11-09", "16:00", "INSTAGRAM")), JSON.createObjectNode()))
                .startsWith("1 propuesta para revisar");
        assertThatThrownBy(() -> handler.apply(week9(), proposals(p("2026-11-09", "15:59", "INSTAGRAM")), JSON.createObjectNode()))
                .hasMessageContaining("1 descartada: 1 con fecha pasada o a menos de 6 h");
    }

    @Test
    void proposalsOutsideThePromotionDatesAreDropped() {
        // Termina el 12 de nov
        when(promotions.datesOf("lit-1")).thenReturn(new PromotionQueries.Dates(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));
        var handler = at(madrid(9, 10, 0));
        String text = handler.apply(week9(), proposals(
                p("2026-11-09", "19:00", "INSTAGRAM"),   // antes de empezar
                p("2026-11-10", "19:00", "INSTAGRAM"),   // primer día: entra
                p("2026-11-12", "19:00", "INSTAGRAM"),   // último día: entra
                p("2026-11-13", "19:00", "INSTAGRAM")), JSON.createObjectNode());
        assertThat(text).startsWith("2 propuestas para revisar en Publicaciones · 2 descartadas: 2 fuera de las fechas de la promoción");
    }

    @Test
    void aPromotionWithoutEndDateHasNoLimit() {
        when(promotions.datesOf("lit-1")).thenReturn(new PromotionQueries.Dates(LocalDate.of(2026, 11, 3), null));
        var handler = at(madrid(9, 10, 0));
        assertThat(handler.apply(week9(), proposals(p("2026-11-15", "19:00", "INSTAGRAM")), JSON.createObjectNode()))
                .startsWith("1 propuesta para revisar").doesNotContain("descartada");
    }

    @Test
    void ifNoneIsValidTheOrderFailsWithTheReasonsAndCreatesNothing() {
        var handler = monday();
        assertThatThrownBy(() -> handler.apply(week9(), proposals(p("2026-11-02", "19:00", "INSTAGRAM"), p("2026-11-16", "19:00", "TIKTOK")),
                JSON.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ninguna propuesta es válida · 2 descartadas: 2 fuera de la semana");
        verify(service, never()).create(any(), any(), any());
    }

    @Test
    void theMaximumAndTheMissingListStillFail() {
        var handler = monday();
        String[] many = new String[WeeklyProposalsHandler.MAX + 1];
        java.util.Arrays.fill(many, p("2026-11-10", "19:00", "INSTAGRAM"));
        assertThatThrownBy(() -> handler.apply(week9(), proposals(many), JSON.createObjectNode()))
                .hasMessageContaining("Demasiadas");
        assertThatThrownBy(() -> handler.apply(week9(), JSON.readTree("{}"), JSON.createObjectNode()))
                .hasMessageContaining("Faltan las propuestas");
    }
}
