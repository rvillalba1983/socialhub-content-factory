package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Un encargo pendiente que ya no llega a la pasada de la noche se reescribe como urgente. */
class AiUrgencyTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @TempDir Path folder;

    private final AiRequestRepository requests = mock(AiRequestRepository.class);
    private final JsonMapper json = JsonMapper.builder().build();

    private static Instant madrid(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute).atZone(MADRID).toInstant();
    }

    private final AiHandlers handlers = mock(AiHandlers.class);
    private final com.socialhub.production.ProductionService production = mock(com.socialhub.production.ProductionService.class);

    private AiUrgency at(Instant now) {
        return new AiUrgency(requests, json, Clock.fixed(now, MADRID), handlers, production, new AiUrgentRule(48));
    }

    private AiRequestEntity request(String name, AiRequestType type, String content, boolean urgent) throws Exception {
        Path file = folder.resolve(name);
        Files.writeString(file, content);
        AiRequestEntity e = new AiRequestEntity();
        e.setTaskId(name);
        e.setType(type.name());
        e.setRequestPath(file.toString());
        e.setUrgent(urgent);
        e.setStatus(AiRequestEntity.Status.SENT);
        return e;
    }

    private boolean urgentIn(String name) throws Exception {
        return json.readTree(Files.readString(folder.resolve(name))).get("urgent").asBoolean();
    }

    @Test
    void aPendingOrderBecomesUrgentOnceTheNightPassNoLongerReachesItsLimit() throws Exception {
        // Se escribió a las 9:00 con límite a las 23:00: la pasada completa de las 20:50 le llegaba; a las 21:00 ya no
        String body = """
                {"type":"socialhub.publer-schedule","deadline":"%s","urgent":false}""".formatted(madrid(9, 23, 0));
        var waiting = request("a.json", AiRequestType.PUBLER_SCHEDULE, body, false);
        when(requests.findByStatus(AiRequestEntity.Status.SENT)).thenReturn(List.of(waiting));
        assertThat(at(madrid(9, 11, 0)).refresh()).isZero();
        assertThat(urgentIn("a.json")).isFalse();
        // Recién pasada la de las 20:50 (y su margen de 90 minutos) no se toca el archivo
        assertThat(at(madrid(9, 21, 0)).refresh()).isZero();
        assertThat(urgentIn("a.json")).isFalse();
        assertThat(at(madrid(9, 22, 30)).refresh()).isEqualTo(1);
        assertThat(urgentIn("a.json")).isTrue();
        // También se actualiza la marca de la base de datos
        assertThat(waiting.isUrgent()).isTrue();
        verify(requests).save(waiting);
        // No se vuelve a tocar
        assertThat(at(madrid(9, 22, 40)).refresh()).isZero();
    }

    @Test
    void ordersWithoutDeadlineOrAlreadyUrgentAreLeftAlone() throws Exception {
        var noDeadline = request("b.json", AiRequestType.WEEKLY_PROPOSALS, "{\"urgent\":false}", false);
        var urgent = request("c.json", AiRequestType.PUBLER_SCHEDULE,
                "{\"deadline\":\"%s\",\"urgent\":true}".formatted(madrid(9, 12, 0)), true);
        var missing = new AiRequestEntity();
        missing.setRequestPath(folder.resolve("no-existe.json").toString());
        missing.setType(AiRequestType.CAPTIONS.name());
        when(requests.findByStatus(AiRequestEntity.Status.SENT)).thenReturn(List.of(noDeadline, urgent, missing));
        assertThat(at(madrid(9, 23, 0)).refresh()).isZero();
        assertThat(urgentIn("b.json")).isFalse();
    }

    @Test
    void aContentOrderBecomesUrgentWhenAPublicationEntersTheNext48HoursWhileItIsStillPending() throws Exception {
        var waiting = request("d.json", AiRequestType.CAPTIONS, "{\"type\":\"socialhub.captions\",\"urgent\":false}", false);
        waiting.setTaskId("t-captions");
        waiting.setBrandId("litoques");
        when(requests.findByStatus(AiRequestEntity.Status.SENT)).thenReturn(List.of(waiting));
        when(production.waitingAiTask("t-captions")).thenReturn(java.util.Optional.of(new com.socialhub.production.AiTask(
                "t-captions", AiRequestType.CAPTIONS.taskType(), "lit-1", "Textos", "goal", List.of("p1"), 1, "SONNET", Instant.EPOCH)));
        var handler = mock(AiTypeHandler.class);
        when(handlers.of(AiRequestType.CAPTIONS)).thenReturn(java.util.Optional.of(handler));
        // La publicación sale el 12 a las 12:00: hasta las 12:00 del 10 (menos los 5 min y 30 s de Drive) no entra en las 48 h
        when(handler.contentDueAt(org.mockito.ArgumentMatchers.any())).thenReturn(madrid(12, 12, 0));
        assertThat(at(madrid(9, 11, 0)).refresh()).isZero();
        assertThat(urgentIn("d.json")).isFalse();
        assertThat(at(madrid(10, 11, 55)).refresh()).isEqualTo(1);
        assertThat(urgentIn("d.json")).isTrue();
        assertThat(waiting.isUrgent()).isTrue();
    }
}
