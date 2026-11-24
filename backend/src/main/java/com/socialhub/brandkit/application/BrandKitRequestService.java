package com.socialhub.brandkit.application;

import com.socialhub.ai.AiOrders;
import com.socialhub.ai.domain.AiRequestType;
import com.socialhub.brandkit.BrandKitRequests;
import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.platform.DomainException;
import com.socialhub.promotion.PromotionQueries;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
class BrandKitRequestService implements BrandKitRequests {

    private final BrandKits kits;
    private final AiOrders orders;
    private final PromotionQueries promotions;

    @Override
    public String request(String brandId, @Nullable String promotionId, List<String> pieces) {
        return order(brandId, promotion(brandId, promotionId, null), pieces, null, null);
    }

    @Override
    public Optional<String> reject(String brandId, long kitId, String comment, @Nullable String promotionId, boolean another) {
        promotions.requireBrand(brandId);
        // Raúl actúa sobre el kit que está viendo: si entretanto llegó otro, o ya se resolvió, no se toca nada
        BrandKit pending = kits.pending(brandId).filter(k -> k.id() != null && k.id() == kitId)
                .orElseThrow(() -> new DomainException(BrandKit.NOT_PENDING));
        if (another) {
            // Antes de rechazar: si ya hay un encargo de kit abierto, no se rechaza ni se pide otro
            kits.openOrderPromotion(brandId).ifPresent(open -> {
                throw new DomainException("Ya hay un encargo de kit abierto de esta marca (promoción «" + open
                        + "»): espera a que Claude lo termine antes de pedir otra versión.");
            });
        }
        // La promoción se resuelve antes de rechazar: si no se puede pedir otra versión, el kit sigue pendiente
        String promotion = another ? promotion(brandId, promotionId, pending.sourceTaskId()) : "";
        BrandKit rejected = kits.reject(java.util.Objects.requireNonNull(pending.id()), comment);
        if (!another) return Optional.empty();
        List<String> pieces = pending.kit().templates().stream().map(t -> t.piece().code()).distinct().toList();
        return Optional.of(order(brandId, promotion, pieces, rejected.comment(), rejected.id()));
    }

    private String order(String brandId, String promotionId, List<String> pieces, @Nullable String comment, @Nullable Long previousKitId) {
        Set<String> codes = new LinkedHashSet<>();
        pieces.forEach(p -> codes.add(KitPiece.fromCode(p.strip()).filter(KitPiece::active).orElseThrow(() ->
                new IllegalArgumentException("«" + p + "» no es una pieza que pueda llevar el kit. Piezas válidas: " + KitPiece.activeCodes() + ".")).code()));
        List<String> inputs = new ArrayList<>();
        codes.forEach(c -> inputs.add(BrandKitHandler.PIECE + c));
        if (comment != null) inputs.add(BrandKitHandler.COMMENT + comment);
        if (previousKitId != null) inputs.add(BrandKitHandler.PREVIOUS + previousKitId);
        String name = promotions.brandRefs().stream().filter(b -> b.id().equals(brandId))
                .map(PromotionQueries.BrandRef::name).findFirst().orElse(brandId);
        String goal = "Diseña el kit de marca de «" + name + "»: la paleta y las plantillas SVG de las piezas pedidas, "
                + "con el formato de contracts/brand-kit.v1.schema.json. Parte de la identidad de la marca (inputs.brand_identity) y "
                + "sigue inputs.template_rules."
                + (comment == null ? "" : " Es otra versión del kit anterior (inputs.previous_kit): aplica este comentario de Raúl: " + comment);
        return orders.submit(promotionId, AiRequestType.BRAND_KIT, "Kit de marca · " + name, goal, inputs);
    }

    /**
     * La promoción de la que cuelga el encargo (decisión 52): la que elige Raúl; si no elige, la del kit rechazado o la única
     * de la marca. Una marca sin promociones no puede pedirlo.
     */
    private String promotion(String brandId, @Nullable String promotionId, @Nullable String sourceTaskId) {
        promotions.requireBrand(brandId);
        List<String> own = promotions.promotionIdsOf(brandId);
        if (own.isEmpty()) {
            throw new IllegalArgumentException("Esta marca aún no tiene ninguna promoción: crea una para poder pedir el kit de marca.");
        }
        if (promotionId != null && !promotionId.isBlank()) {
            if (!own.contains(promotionId)) throw new IllegalArgumentException("La promoción «" + promotionId + "» no es de esta marca.");
            return promotionId;
        }
        if (sourceTaskId != null) {
            Optional<String> same = orders.promotionOf(sourceTaskId).filter(own::contains);
            if (same.isPresent()) return same.get();
        }
        if (own.size() == 1) return own.getFirst();
        throw new IllegalArgumentException("Elige la promoción de la que cuelga el encargo: la marca tiene " + own.size() + ".");
    }
}
