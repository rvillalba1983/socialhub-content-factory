package com.socialhub.pieces.application;

import com.socialhub.pieces.domain.LoadedSpec;
import com.socialhub.pieces.domain.SpecLoader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lee un spec.json dentro de las carpetas de la promoción y lo valida. */
@Component
@RequiredArgsConstructor
class SpecReader {

    private final SpecLoader loader;
    private final PieceGenerator generator;

    LoadedSpec load(String specPath, List<String> folders) {
        LoadedSpec loaded = loader.read(specPath, folders);
        generator.validate(loaded.spec(), loaded.files());
        return loaded;
    }
}
