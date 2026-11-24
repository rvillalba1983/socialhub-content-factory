package com.socialhub.brandkit;

import com.socialhub.brandkit.domain.TemplateChecker;
import com.socialhub.pieces.adapter.out.svg.BatikSvgRasterizer;
import com.socialhub.pieces.adapter.out.svg.SvgTemplateRenderer;
import com.socialhub.pieces.domain.TemplateCheck;
import com.socialhub.pieces.domain.TemplateField;

/** Revisor de plantillas de las pruebas unitarias: el motor real de piezas, sin Spring. */
public final class KitTemplateChecker implements TemplateChecker {

    private final SvgTemplateRenderer renderer = new SvgTemplateRenderer(new BatikSvgRasterizer());

    @Override
    public Result check(String svg) {
        TemplateCheck check = renderer.check(svg);
        return new Result(check.width(), check.height(),
                check.fields().stream().filter(f -> !f.fixed()).map(TemplateField::name).toList(),
                check.fields().stream().filter(TemplateField::fixed).map(TemplateField::name).toList(), check.errors());
    }
}
