package com.socialhub.brandkit.application;

import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.BrandKitRepository;
import com.socialhub.brandkit.domain.InvalidKitException;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitContent;
import com.socialhub.brandkit.domain.KitStatus;
import com.socialhub.brandkit.domain.KitTemplate;
import com.socialhub.brandkit.domain.KitValidator;
import com.socialhub.brandkit.domain.TemplateChecker;
import com.socialhub.brandkit.domain.TemplatePreview;
import com.socialhub.brandkit.domain.TemplatePreviewer;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.platform.DomainException;
import com.socialhub.platform.NotFoundException;
import com.socialhub.production.ProductionAiTasks;
import com.socialhub.promotion.PromotionQueries;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class BrandKitService implements BrandKits {

    static final int MAX_COMMENT = 1000;
    /** Ancho de las vistas previas, en píxeles. */
    static final int PREVIEW_WIDTH = 540;
    /** Vistas previas que se recuerdan: dos kits (el aprobado y el pendiente) de 12 plantillas, con holgura. */
    static final int MAX_PAINTED = 64;

    private final BrandKitRepository kits;
    private final KitCodec codec;
    private final KitValidator validator;
    private final Clock clock;
    private final TemplatePreviewer previewer;
    private final ProductionAiTasks aiTasks;
    private final PromotionQueries promotions;
    /**
     * Vistas previas ya pintadas, por el SVG (su huella): el contenido de una plantilla no cambia, así que recargar la ficha, comprobar
     * el kit al aprobarlo o guardarlo tras comprobarlo no pintan dos veces. Acotado: se descartan las más antiguas.
     */
    private final Map<String, TemplatePreviewer.Result> painted = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, TemplatePreviewer.Result> eldest) {
                    return size() > MAX_PAINTED;
                }
            });

    BrandKitService(BrandKitRepository kits, KitCodec codec, TemplateChecker checker, TemplatePreviewer previewer, Clock clock,
                    ProductionAiTasks aiTasks, PromotionQueries promotions) {
        this.codec = codec;
        this.previewer = previewer;
        this.kits = kits;
        this.validator = new KitValidator(checker);
        this.clock = clock;
        this.aiTasks = aiTasks;
        this.promotions = promotions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> check(String kitJson) {
        try {
            return problems(codec.read(kitJson));
        } catch (InvalidKitException e) {
            return e.errors();
        }
    }

    /** Los errores del kit y, si no tiene ninguno, los de pintar cada plantilla: un kit que no se pinta no se propone ni se aprueba. */
    private List<String> problems(KitContent content) {
        List<String> errors = validator.validate(content);
        if (!errors.isEmpty()) return errors;
        return paintingErrors(content);
    }

    private List<String> paintingErrors(KitContent content) {
        List<String> errors = new java.util.ArrayList<>();
        for (KitTemplate template : content.templates()) {
            TemplatePreviewer.Result r = paint(template.svg());
            if (r.png() == null || !r.errors().isEmpty()) {
                errors.add("La plantilla «" + template.id() + "» no se pinta: "
                        + (r.errors().isEmpty() ? "no se pudo generar la vista previa." : String.join(" ", r.errors())));
            }
        }
        return errors;
    }

    private TemplatePreviewer.Result paint(String svg) {
        String key = fingerprint(svg);
        TemplatePreviewer.Result known = painted.get(key);
        if (known != null) return known;
        TemplatePreviewer.Result result = previewer.preview(svg, PREVIEW_WIDTH);
        painted.put(key, result);
        return result;
    }

    private static String fingerprint(String svg) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(svg.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public BrandKit propose(String brandId, String kitJson, @Nullable String sourceTaskId) {
        if (!kits.brandExists(brandId)) {
            throw new NotFoundException("No existe la marca «" + brandId + "».");
        }
        KitContent content = codec.read(kitJson);
        List<String> errors = problems(content);
        if (!errors.isEmpty()) throw new InvalidKitException(errors);
        // Un kit nuevo sustituye al que seguía esperando la decisión de Raúl
        kits.findByStatus(brandId, KitStatus.PENDING)
                .ifPresent(old -> kits.save(old.withStatus(KitStatus.SUPERSEDED, old.comment(), null)));
        return kits.save(new BrandKit(null, brandId, kits.nextVersion(brandId), KitStatus.PENDING, content,
                codec.write(content), null, sourceTaskId, Instant.now(clock), null));
    }

    @Override
    public BrandKit approve(long id) {
        BrandKit kit = pendingKit(id);
        // Se vuelve a comprobar al aprobar: un kit con una plantilla que no se pinta no puede pasar a ser el kit en vigor
        List<String> notPainted = paintingErrors(kit.kit());
        if (!notPainted.isEmpty()) {
            throw new DomainException("No se puede aprobar este kit: " + String.join(" ", notPainted) + " Pide otra versión.");
        }
        kits.findByStatus(kit.brandId(), KitStatus.APPROVED)
                .ifPresent(old -> kits.save(old.withStatus(KitStatus.SUPERSEDED, old.comment(), old.approvedAt())));
        return kits.save(kit.withStatus(KitStatus.APPROVED, kit.comment(), Instant.now(clock)));
    }

    @Override
    public BrandKit reject(long id, String comment) {
        String text = comment.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Escribe qué quieres cambiar para pedir otra versión.");
        if (text.length() > MAX_COMMENT) throw new IllegalArgumentException("El comentario supera los " + MAX_COMMENT + " caracteres.");
        BrandKit kit = pendingKit(id);
        return kits.save(kit.withStatus(KitStatus.REJECTED, text, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandKit> find(long id) { return kits.find(id); }

    @Override
    public List<TemplatePreview> previews(BrandKit kit) {
        return kit.kit().templates().stream().map(t -> {
            TemplatePreviewer.Result r = paint(t.svg());
            byte[] png = r.png();
            return new TemplatePreview(t.id(), png == null ? null : Base64.getEncoder().encodeToString(png), r.warnings(), r.errors());
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandKit> approved(String brandId) { return kits.findByStatus(brandId, KitStatus.APPROVED); }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandKit> pending(String brandId) { return kits.findByStatus(brandId, KitStatus.PENDING); }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> openOrderPromotion(String brandId) {
        for (String promotionId : promotions.promotionIdsOf(brandId)) {
            boolean open = aiTasks.coveringAiTasks(AiRequestType.BRAND_KIT.taskType(), promotionId).stream()
                    .anyMatch(c -> c.cover() == ProductionAiTasks.Cover.OPEN);
            if (open) return Optional.of(promotionId);
        }
        return Optional.empty();
    }

    private BrandKit pendingKit(long id) {
        BrandKit kit = kits.find(id).orElseThrow(() -> new NotFoundException("No existe el kit " + id + "."));
        if (kit.status() != KitStatus.PENDING) {
            throw new DomainException(BrandKit.NOT_PENDING + " (Este está " + describe(kit.status()) + ".)");
        }
        return kit;
    }

    private static String describe(KitStatus status) {
        return switch (status) {
            case PENDING -> "pendiente";
            case APPROVED -> "aprobado";
            case SUPERSEDED -> "sustituido por otro";
            case REJECTED -> "rechazado";
        };
    }
}
