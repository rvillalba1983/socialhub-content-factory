package com.socialhub.brandkit.application;

import com.socialhub.brandkit.domain.TemplatePreviewer;
import com.socialhub.pieces.TemplateChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Pinta las vistas previas con el motor del módulo de piezas. */
@Component
@RequiredArgsConstructor
class PiecesTemplatePreviewer implements TemplatePreviewer {

    private final TemplateChecks checks;

    @Override
    public Result preview(String svg, int pixelWidth) {
        TemplateChecks.Preview preview = checks.preview(svg, pixelWidth);
        return new Result(preview.png(), preview.warnings(), preview.errors());
    }
}
