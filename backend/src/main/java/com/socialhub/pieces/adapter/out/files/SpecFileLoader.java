package com.socialhub.pieces.adapter.out.files;

import com.socialhub.pieces.domain.LoadedSpec;
import com.socialhub.pieces.domain.PieceFiles;
import com.socialhub.pieces.domain.PromoSpec;
import com.socialhub.pieces.domain.SpecLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Lee un spec.json del disco, dentro de las carpetas de la promoción. */
@Component
@RequiredArgsConstructor
public class SpecFileLoader implements SpecLoader {

    private static final long MAX_BYTES = 1024 * 1024;

    private final ObjectMapper json;

    @Override
    public LoadedSpec read(String specPath, List<String> folders) {
        Path path = Path.of(specPath);
        if (!path.isAbsolute()) throw new IllegalArgumentException("La ruta del spec debe ser completa: " + specPath);
        PieceFiles files = new PieceFiles(folders, path.toAbsolutePath().getParent());
        Path real = files.input(path.toString(), Set.of("json"), 1);
        try {
            if (Files.size(real) > MAX_BYTES) throw new IllegalArgumentException("El spec pasa de 1 MB");
            PromoSpec spec = json.readValue(Files.readString(real), PromoSpecJson.class).toDomain();
            return new LoadedSpec(real, spec, files);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("El spec no es un JSON válido: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("No se puede leer el spec: " + e.getMessage());
        }
    }
}
