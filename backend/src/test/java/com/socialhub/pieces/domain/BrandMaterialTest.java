package com.socialhub.pieces.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrandMaterialTest {

    @TempDir Path material;

    private Path touch(String relative) throws Exception {
        Path file = material.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.write(file, new byte[] {1});
    }

    @Test
    void coverIsPortadaWithAnyCaseAndExtensionInTheRootOnly() throws Exception {
        assertThat(BrandMaterial.cover(material)).isEmpty();
        touch("sub/portada.jpg"); // en una subcarpeta no vale
        touch("portada.gif");
        touch("mi portada.jpg");
        assertThat(BrandMaterial.cover(material)).isEmpty();
        touch("PORTADA.PNG");
        assertThat(BrandMaterial.cover(material)).get().extracting(p -> p.getFileName().toString()).isEqualTo("PORTADA.PNG");
        touch("Portada.jpeg");
        assertThat(BrandMaterial.cover(material)).get().extracting(p -> p.getFileName().toString()).isEqualTo("Portada.jpeg");
        touch("portada.jpg");
        assertThat(BrandMaterial.cover(material)).get().extracting(p -> p.getFileName().toString()).isEqualTo("portada.jpg");
    }

    @Test
    void coverOfAFolderThatDoesNotExistIsEmpty() {
        assertThat(BrandMaterial.cover(material.resolve("no-existe"))).isEmpty();
    }

    @Test
    void aPublicationHasMaterialWhenItsKeywordsAppearInAFilePathIgnoringAccentsAndCase() throws Exception {
        touch("Canciones/Con púa/foto 1.JPG");
        touch("pregunta_grande.mp4");
        touch("notas.txt");
        var scan = BrandMaterial.scan(List.of(material));
        assertThat(scan.accessible()).isTrue();
        assertThat(scan.covers("Reel: Con púa")).isTrue();
        assertThat(scan.covers("CON PUA")).isTrue();
        assertThat(scan.covers("Pregunta grande")).isTrue();
        assertThat(scan.covers("Canción nueva sin foto")).isFalse();
        assertThat(scan.covers("notas")).isFalse(); // un .txt no es imagen ni vídeo
    }

    @Test
    void theCoverDoesNotCountAsMaterialOfASongAndTitlesWithoutKeywordsAreNotFlagged() throws Exception {
        touch("portada.jpg");
        var scan = BrandMaterial.scan(List.of(material));
        assertThat(scan.covers("Portada")).isFalse();
        assertThat(scan.covers("Reel")).isTrue(); // solo palabras vacías: no se puede decidir, no se avisa
    }

    @Test
    void foldersThatAreNotAvailableAreReportedAsNotAccessible() {
        var scan = BrandMaterial.scan(List.of(material.resolve("falta")));
        assertThat(scan.accessible()).isFalse();
        assertThat(BrandMaterial.scan(List.of()).accessible()).isFalse();
        assertThat(BrandMaterial.scan(List.of(material.resolve("falta"), material)).accessible()).isTrue();
    }

    @Test
    void normalizeStripsAccentsAndPunctuation() {
        assertThat(BrandMaterial.normalize("  Ñandú — Canción #1! ")).isEqualTo("nandu cancion 1");
        assertThat(BrandMaterial.keywords("El reel de la Púa")).containsExactly("púa".replace("ú", "u"));
    }
}
