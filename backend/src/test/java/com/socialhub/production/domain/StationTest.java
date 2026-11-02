package com.socialhub.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import org.junit.jupiter.api.Test;

class StationTest {

    private static Station station(StatusLevel status) {
        return Station.builder().id("gen").name("Generación").status(status).build();
    }

    @Test
    void pausingKeepsThePreviousStatusToRestoreIt() {
        Station s = station(StatusLevel.RUNNING);
        s.pause();
        assertThat(s.getStatus()).isEqualTo(StatusLevel.WAITING);
        assertThat(s.acceptsWork()).isFalse();
        assertThat(s.availableCommands()).containsExactly(StationCommandType.RESUME);
        assertThatThrownBy(s::pause).isInstanceOf(DomainException.class);

        s.resume();
        assertThat(s.getStatus()).isEqualTo(StatusLevel.RUNNING);
        assertThat(s.acceptsWork()).isTrue();
        assertThatThrownBy(s::resume).isInstanceOf(DomainException.class);
    }

    @Test
    void blockedStationDoesNotAcceptWork() {
        assertThat(station(StatusLevel.BLOCKED).acceptsWork()).isFalse();
    }
}
