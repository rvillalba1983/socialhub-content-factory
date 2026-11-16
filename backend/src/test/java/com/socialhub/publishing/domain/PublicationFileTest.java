package com.socialhub.publishing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublicationFileTest {

    private static PublicationFile file(String path) {
        return new PublicationFile(path, PublicationFile.Origin.SPEC, Instant.EPOCH);
    }

    @Test
    void imagesAndVideosHaveTheirMimeTypeWhateverTheCase() {
        assertThat(file("G:\\Drive\\a.JPG").mimeType()).isEqualTo("image/jpeg");
        assertThat(file("/x/a.png").kind()).isEqualTo(PublicationFile.Kind.IMAGE);
        assertThat(file("/x/a.webp").mimeType()).isEqualTo("image/webp");
        assertThat(file("/x/clip.mp4").mimeType()).isEqualTo("video/mp4");
        assertThat(file("/x/clip.mov").mimeType()).isEqualTo("video/quicktime");
        assertThat(file("/x/clip.webm").kind()).isEqualTo(PublicationFile.Kind.VIDEO);
    }

    @Test
    void anythingElseCannotBePreviewed() {
        assertThat(file("/x/notas.txt").mimeType()).isNull();
        assertThat(file("/x/notas.txt").kind()).isEqualTo(PublicationFile.Kind.OTHER);
        assertThat(file("/x/sin-extension").mimeType()).isNull();
        assertThat(file("/x.jpg/archivo").mimeType()).isNull();
        assertThat(file("/x/.jpg").mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void theIdIsStableOpaqueAndDifferentPerPath() {
        assertThat(file("/x/a.jpg").id()).isEqualTo(file("/x/a.jpg").id()).matches("[0-9a-f]{16}");
        assertThat(file("/x/a.jpg").id()).isNotEqualTo(file("/x/b.jpg").id());
    }
}
