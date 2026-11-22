package com.socialhub.publishing.adapter.out.documents;

import com.socialhub.publishing.domain.DocumentReader;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Lee txt y md (UTF-8) y PDF con texto (Apache PDFBox, sin OCR). Límites: tamaño del archivo, páginas y tiempo; si se
 * pasa de alguno, el documento cuenta como no legible (la cita queda «sin verificar», nunca «verificada»).
 */
@Component
class FileDocumentReader implements DocumentReader {

    static final long MAX_TEXT_BYTES = 5L * 1024 * 1024;
    static final long MAX_PDF_BYTES = 25L * 1024 * 1024;
    static final int MAX_PAGES = 300;
    static final Duration MAX_TIME = Duration.ofSeconds(20);

    static {
        // Sin pantalla (el instalador no tiene): PDFBox no necesita AWT para extraer texto
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public Reading read(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            long size = Files.size(file);
            if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown")) return text(file, size);
            if (name.endsWith(".pdf")) return pdf(file, size);
            return Reading.unreadable("formato sin soporte (solo txt, md y PDF con texto)");
        } catch (IOException | RuntimeException e) {
            return Reading.unreadable("no se pudo leer el documento");
        }
    }

    private static Reading text(Path file, long size) throws IOException {
        if (size > MAX_TEXT_BYTES) return Reading.unreadable("documento demasiado grande (más de 5 MB)");
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String text = decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file))).toString();
            if (text.startsWith("﻿")) text = text.substring(1);
            return new Reading(true, "línea", List.of(text), null);
        } catch (CharacterCodingException e) {
            return Reading.unreadable("el texto no está en UTF-8");
        }
    }

    private static Reading pdf(Path file, long size) throws IOException {
        if (size > MAX_PDF_BYTES) return Reading.unreadable("PDF demasiado grande (más de 25 MB)");
        long deadline = System.nanoTime() + MAX_TIME.toNanos();
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (doc.isEncrypted()) return Reading.unreadable("PDF protegido con contraseña");
            int pages = doc.getNumberOfPages();
            if (pages > MAX_PAGES) return Reading.unreadable("PDF con más de " + MAX_PAGES + " páginas");
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> parts = new ArrayList<>();
            long chars = 0;
            for (int page = 1; page <= pages; page++) {
                if (System.nanoTime() > deadline) return Reading.unreadable("el PDF tarda demasiado en leerse");
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                chars += text.strip().length();
                parts.add(text);
            }
            if (chars < 20) return Reading.unreadable("el PDF no tiene texto (¿está escaneado?)");
            return new Reading(true, "página", parts, null);
        }
    }
}
