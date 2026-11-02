package com.socialhub.ai;

import com.socialhub.promotion.PromotionQueries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Guarda un informe de Claude (Markdown) en la carpeta de Informes de la promoción, sin sobrescribir nada. */
@Component
@RequiredArgsConstructor
public class AiReports {

    static final int MAX_CHARS = 200_000;

    private final PromotionQueries promotions;
    private final Clock clock;

    /**
     * @param name base del nombre del archivo, por ejemplo «balance-semanal»
     * @return texto de resultado de la tarea
     */
    public String save(AiJob job, String name, JsonNode outputs, JsonNode result) {
        JsonNode report = outputs.path("report");
        if (!report.isString() || report.asString().isBlank()) {
            throw new IllegalArgumentException("Falta el informe («outputs.report», en Markdown)");
        }
        if (report.asString().length() > MAX_CHARS) throw new IllegalArgumentException("El informe es demasiado largo");
        StringBuilder text = new StringBuilder(report.asString().strip()).append('\n');
        if (result.path("sources").size() > 0) {
            text.append("\n## Fuentes\n\n");
            result.path("sources").forEach(s -> text.append("- [").append(s.path("title").asString())
                    .append("](").append(s.path("url").asString()).append(")\n"));
        }
        String summary = outputs.path("summary").isString() && !outputs.path("summary").asString().isBlank()
                ? outputs.path("summary").asString().strip() : job.title();
        Optional<String> folder = promotions.reportsFolder(job.promotionId());
        if (folder.isEmpty() || !Files.isDirectory(Path.of(folder.get()))) {
            return summary + " · La promoción no tiene carpeta de «Informes» accesible: el informe está en el resultado de Claude.";
        }
        try {
            Path dir = Path.of(folder.get());
            Path file = fresh(dir, LocalDate.now(clock) + "-" + slug(name));
            Files.writeString(file, text.toString());
            return summary + " · Informe: " + file;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el informe: " + e.getMessage(), e);
        }
    }

    static String slug(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "informe" : s.substring(0, Math.min(60, s.length()));
    }

    private static Path fresh(Path dir, String name) {
        Path candidate = dir.resolve(name + ".md");
        for (int n = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); n++) candidate = dir.resolve(name + "_" + n + ".md");
        return candidate;
    }
}
