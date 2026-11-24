package com.socialhub.pieces.application;

import com.socialhub.pieces.TemplateChecks;
import com.socialhub.pieces.domain.TemplateCheck;
import com.socialhub.pieces.domain.TemplateField;
import com.socialhub.pieces.domain.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TemplateChecksService implements TemplateChecks {

    private final TemplateRenderer renderer;

    @Override
    public Report check(String templateSvg) {
        TemplateCheck check = renderer.check(templateSvg);
        return new Report(check.width(), check.height(),
                check.fields().stream().filter(f -> !f.fixed()).map(TemplateField::name).toList(),
                check.fields().stream().filter(TemplateField::fixed).map(TemplateField::name).toList(),
                check.errors());
    }
}
