package com.socialhub.pieces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Lee un spec.json dentro de las carpetas de la promoción y lo valida. */
@Component
class SpecReader {

    private static final long MAX_BYTES = 1024 * 1024;

    private final ObjectMapper json;
    private final PieceGenerator generator;

    SpecReader(ObjectMapper json, PieceGenerator generator) {
        this.json = json;
        this.generator = generator;
    }

    record Loaded(Path path, PromoSpec spec, PieceFiles files) {}

    Loaded load(String specPath, java.util.List<String> folders) {
        Path path = Path.of(specPath);
        if (!path.isAbsolute()) throw new IllegalArgumentException("La ruta del spec debe ser completa: " + specPath);
        PieceFiles files = new PieceFiles(folders, path.toAbsolutePath().getParent());
        Path real = files.input(path.toString(), java.util.Set.of("json"), 1);
        try {
            if (Files.size(real) > MAX_BYTES) throw new IllegalArgumentException("El spec pasa de 1 MB");
            PromoSpec spec = json.readValue(Files.readString(real), PromoSpec.class);
            generator.validate(spec, files);
            return new Loaded(real, spec, files);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("El spec no es un JSON válido: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("No se puede leer el spec: " + e.getMessage());
        }
    }
}
