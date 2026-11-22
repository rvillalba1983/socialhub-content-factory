package com.socialhub.publishing.adapter.out.documents;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.publishing.domain.DocumentReader.Reading;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDocumentReaderTest {

    @TempDir Path dir;
    private final FileDocumentReader reader = new FileDocumentReader();

    @Test
    void readsPlainTextAndMarkdown() throws Exception {
        Path txt = Files.writeString(dir.resolve("a.txt"), "﻿Verso uno\nVerso dos", StandardCharsets.UTF_8);
        Reading r = reader.read(txt);
        assertThat(r.readable()).isTrue();
        assertThat(r.unit()).isEqualTo("línea");
        assertThat(r.parts()).containsExactly("Verso uno\nVerso dos");
        assertThat(reader.read(Files.writeString(dir.resolve("b.md"), "# Título")).readable()).isTrue();
    }

    @Test
    void textThatIsNotUtf8OrIsTooBigIsUnreadable() throws Exception {
        Path latin1 = Files.write(dir.resolve("l.txt"), "corazón".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(reader.read(latin1).readable()).isFalse();
        Path big = dir.resolve("big.txt");
        try (var out = Files.newOutputStream(big)) { out.write(new byte[(int) FileDocumentReader.MAX_TEXT_BYTES + 1]); }
        assertThat(reader.read(big).reason()).contains("demasiado grande");
    }

    @Test
    void readsEachPdfPageSeparatelyHeadless() throws Exception {
        Path pdf = pdf("t.pdf", "Portada del libro", "Segunda pagina con versos");
        Reading r = reader.read(pdf);
        assertThat(r.readable()).isTrue();
        assertThat(r.unit()).isEqualTo("página");
        assertThat(r.parts()).hasSize(2);
        assertThat(r.parts().get(1)).contains("Segunda pagina con versos");
        assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
    }

    @Test
    void aPdfWithoutTextOrBrokenOrOfAnotherFormatIsUnreadableNeverVerified() throws Exception {
        assertThat(reader.read(pdf("scan.pdf", "", "")).reason()).contains("escaneado");
        assertThat(reader.read(Files.writeString(dir.resolve("roto.pdf"), "esto no es un pdf")).readable()).isFalse();
        assertThat(reader.read(Files.writeString(dir.resolve("c.docx"), "x")).reason()).contains("formato sin soporte");
        assertThat(reader.read(dir.resolve("no-existe.txt")).readable()).isFalse();
    }

    private Path pdf(String name, String... pages) throws Exception {
        Path file = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text.isEmpty()) continue;
                try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                    out.beginText(); out.setFont(font, 12); out.newLineAtOffset(50, 700); out.showText(text); out.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }
}
