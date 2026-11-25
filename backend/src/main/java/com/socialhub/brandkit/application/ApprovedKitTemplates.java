package com.socialhub.brandkit.application;

import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.brandkit.domain.KitTemplate;
import com.socialhub.pieces.ApprovedTemplates;
import com.socialhub.pieces.TemplateChecks;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Da al módulo de piezas las plantillas del kit aprobado de la marca (puerto {@link ApprovedTemplates}). El módulo de
 * piezas no depende del kit: es el kit quien se enchufa a él.
 */
@Component
@RequiredArgsConstructor
class ApprovedKitTemplates implements ApprovedTemplates {

    /** Perezoso: los manejadores de encargos de IA (que usan este puerto) y el kit se necesitan entre sí al arrancar. */
    private final ObjectProvider<BrandKits> kits;
    private final TemplateChecks checks;

    @Override
    public List<Template> of(String brandId) {
        return kits.getObject().approved(brandId).map(kit -> kit.kit().templates().stream().map(this::toTemplate).toList())
                .orElse(List.of());
    }

    private Template toTemplate(KitTemplate template) {
        TemplateChecks.Report report = checks.check(template.svg());
        KitPiece piece = template.piece();
        String ratio = piece.ratios().stream().filter(r -> r.matches(report.width(), report.height()))
                .map(KitPiece.Ratio::label).findFirst().orElse(report.width() + ":" + report.height());
        Zone zone = template.image() == null ? null
                : new Zone(template.image().x(), template.image().y(), template.image().width(), template.image().height());
        // Los obligatorios y opcionales que la plantilla de verdad tiene (un campo fijo no lo manda Claude)
        List<String> required = piece.requiredFields().stream().filter(report.fields()::contains).toList();
        List<String> optional = report.fields().stream().filter(f -> !required.contains(f)).toList();
        return new Template(template.id(), piece.code(), template.name(), template.svg(), zone, required, optional,
                report.fixed(), ratio, report.width(), report.height());
    }
}
