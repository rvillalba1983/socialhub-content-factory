package com.socialhub.pieces.application;

import com.socialhub.pieces.TemplateChecks;
import com.socialhub.pieces.domain.TemplateCheck;
import com.socialhub.pieces.domain.TemplateField;
import com.socialhub.pieces.domain.TemplateRenderer;
import com.socialhub.pieces.domain.TemplateRender;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TemplateChecksService implements TemplateChecks {

    private final TemplateRenderer renderer;

    @Override
    public Preview preview(String templateSvg, int pixelWidth) {
        TemplateCheck check = renderer.check(templateSvg);
        if (!check.valid()) return new Preview(null, List.of(), check.errors());
        int height = (int) Math.round((double) pixelWidth * check.height() / check.width());
        TemplateRender render = renderer.preview(templateSvg, pixelWidth, Math.max(1, height));
        if (!render.ok()) return new Preview(null, render.warnings(), render.errors());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(Objects.requireNonNull(render.image()), "png", out)) {
                return new Preview(null, render.warnings(), List.of("No se pudo generar la imagen de la vista previa."));
            }
            return new Preview(out.toByteArray(), render.warnings(), List.of());
        } catch (IOException e) {
            return new Preview(null, render.warnings(), List.of("No se pudo generar la imagen de la vista previa."));
        }
    }

    @Override
    public Report check(String templateSvg) {
        TemplateCheck check = renderer.check(templateSvg);
        return new Report(check.width(), check.height(),
                check.fields().stream().filter(f -> !f.fixed()).map(TemplateField::name).toList(),
                check.fields().stream().filter(TemplateField::fixed).map(TemplateField::name).toList(),
                check.errors());
    }
}
