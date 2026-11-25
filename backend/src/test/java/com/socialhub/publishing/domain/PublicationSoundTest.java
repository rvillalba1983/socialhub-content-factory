package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Sonido de una publicación (decisión 23): sin sonido, tema de Litoques en el vídeo o tema que se elige en la red. */
class PublicationSoundTest {

    private static Publication at(PublicationStatus status) {
        return Publication.builder().id("p").promotionId("promo").platform(Platform.TIKTOK).type("Reel")
                .title("Prueba").scheduledOn(LocalDate.of(2026, 11, 2)).scheduledTime(LocalTime.of(19, 0))
                .status(status).confirmation(status == PublicationStatus.PUBLISHED ? "https://www.tiktok.com/@x/video/1" : null).build();
    }

    @Test
    void aPublicationStartsWithoutSound() {
        assertThat(at(PublicationStatus.IN_PRODUCTION).getSound()).isEqualTo(PublicationSound.NONE);
        assertThat(PublicationSound.NONE.label()).isEqualTo("Sin sonido");
    }

    @Test
    void theThreeModesHaveTheirLabelAndNeedTheTrackName() {
        assertThat(PublicationSound.of("none", null).mode()).isEqualTo(PublicationSound.Mode.NONE);
        assertThat(PublicationSound.of("litoques_track", " Púa ").label()).isEqualTo("Tema de Litoques en el vídeo: Púa");
        assertThat(PublicationSound.of("network", "Púa").label()).isEqualTo("Elegir en la red: Púa");
        assertThatThrownBy(() -> PublicationSound.of("network", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Falta el nombre del tema");
        assertThatThrownBy(() -> PublicationSound.of("litoques_track", "  ")).hasMessageContaining("Falta el nombre del tema");
        assertThatThrownBy(() -> PublicationSound.of("loud", "x")).hasMessageContaining("Sonido no válido");
        assertThatThrownBy(() -> PublicationSound.of("network", "x".repeat(201))).hasMessageContaining("200 caracteres");
    }

    @Test
    void withoutSoundTheTrackIsDropped() {
        assertThat(PublicationSound.of("none", "Púa").track()).isNull();
    }

    @Test
    void theSoundCanBeChangedWhileEditableButNotOnceScheduledOrPublished() {
        Publication p = at(PublicationStatus.IN_PRODUCTION);
        p.setSound(PublicationSound.of("litoques_track", "Púa"));
        assertThat(p.getSound().mode()).isEqualTo(PublicationSound.Mode.LITOQUES_TRACK);
        for (PublicationStatus locked : new PublicationStatus[] {PublicationStatus.SCHEDULED, PublicationStatus.PUBLISHING, PublicationStatus.PUBLISHED}) {
            assertThatThrownBy(() -> at(locked).setSound(PublicationSound.NONE)).isInstanceOf(DomainException.class);
        }
    }
}
