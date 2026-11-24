package com.socialhub.brandkit.application;

import com.socialhub.brandkit.domain.TemplateChecker;
import com.socialhub.pieces.TemplateChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Revisa las plantillas con el motor del módulo de piezas. */
@Component
@RequiredArgsConstructor
class PiecesTemplateChecker implements TemplateChecker {

    private final TemplateChecks checks;

    @Override
    public Result check(String svg) {
        TemplateChecks.Report report = checks.check(svg);
        return new Result(report.width(), report.height(), report.fields(), report.fixed(), report.errors());
    }
}
