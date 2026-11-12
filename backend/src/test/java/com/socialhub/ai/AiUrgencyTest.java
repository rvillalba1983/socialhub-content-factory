package com.socialhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    private AiUrgency at(Instant now) { return new AiUrgency(requests, json, Clock.fixed(now, MADRID)); }

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
        assertThat(at(madrid(9, 21, 0)).refresh()).isEqualTo(1);
        assertThat(urgentIn("a.json")).isTrue();
        // No se vuelve a tocar
        assertThat(at(madrid(9, 21, 5)).refresh()).isZero();
    }

    @Test
    void ordersWithoutDeadlineOrAlreadyUrgentAreLeftAlone() throws Exception {
        var noDeadline = request("b.json", AiRequestType.CAPTIONS, "{\"urgent\":false}", false);
        var urgent = request("c.json", AiRequestType.PUBLER_SCHEDULE,
                "{\"deadline\":\"%s\",\"urgent\":true}".formatted(madrid(9, 12, 0)), true);
        var missing = new AiRequestEntity();
        missing.setRequestPath(folder.resolve("no-existe.json").toString());
        missing.setType(AiRequestType.CAPTIONS.name());
        when(requests.findByStatus(AiRequestEntity.Status.SENT)).thenReturn(List.of(noDeadline, urgent, missing));
        assertThat(at(madrid(9, 23, 0)).refresh()).isZero();
        assertThat(urgentIn("b.json")).isFalse();
    }
}
