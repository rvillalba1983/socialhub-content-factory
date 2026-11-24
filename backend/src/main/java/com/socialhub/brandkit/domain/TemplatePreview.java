package com.socialhub.brandkit.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Vista previa de una plantilla del kit con textos de ejemplo: PNG codificado en base64 (nulo si no se pudo pintar). */
public record TemplatePreview(String templateId, @Nullable String pngBase64, List<String> warnings, List<String> errors) {}
