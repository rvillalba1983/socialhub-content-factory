package com.socialhub.brandkit.adapter.in.web;

import com.socialhub.api.BrandKitApi;
import com.socialhub.api.model.AiOrderDto;
import com.socialhub.api.model.BrandKitApproveInputDto;
import com.socialhub.api.model.BrandKitColorDto;
import com.socialhub.api.model.BrandKitRejectInputDto;
import com.socialhub.api.model.BrandKitRejectionDto;
import com.socialhub.api.model.BrandKitRequestInputDto;
import com.socialhub.api.model.BrandKitTemplateDto;
import com.socialhub.api.model.BrandKitTemplateImageDto;
import com.socialhub.api.model.BrandKitVersionDto;
import com.socialhub.api.model.BrandKitViewDto;
import com.socialhub.brandkit.BrandKitRequests;
import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.KitTemplate;
import com.socialhub.brandkit.domain.TemplatePreview;
import com.socialhub.platform.DomainException;
import com.socialhub.promotion.PromotionQueries;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BrandKitController implements BrandKitApi {

    private final BrandKits kits;
    private final BrandKitRequests requests;
    private final PromotionQueries promotions;

    @Override
    public ResponseEntity<BrandKitViewDto> getBrandKit(String brandId) {
        promotions.requireBrand(brandId);
        BrandKitViewDto view = new BrandKitViewDto();
        kits.approved(brandId).ifPresent(k -> view.setApproved(toDto(k)));
        kits.pending(brandId).ifPresent(k -> view.setPending(toDto(k)));
        return ResponseEntity.ok(view);
    }

    @Override
    public ResponseEntity<AiOrderDto> requestBrandKit(String brandId, String requestGuard, BrandKitRequestInputDto input) {
        List<String> pieces = input.getPieces() == null ? List.of() : input.getPieces().stream().map(p -> p.getValue()).toList();
        String taskId = requests.request(brandId, input.getPromotionId(), pieces);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AiOrderDto(taskId));
    }

    @Override
    public ResponseEntity<BrandKitVersionDto> approveBrandKit(String brandId, String requestGuard, BrandKitApproveInputDto input) {
        promotions.requireBrand(brandId);
        // Raúl aprueba el kit que está viendo: si ya no es el pendiente de la marca, 409 y no se aprueba otro por él
        BrandKit pending = kits.pending(brandId).filter(k -> id(k) == input.getKitId())
                .orElseThrow(() -> new DomainException(BrandKit.NOT_PENDING));
        return ResponseEntity.ok(toDto(kits.approve(id(pending))));
    }

    @Override
    public ResponseEntity<BrandKitRejectionDto> rejectBrandKit(String brandId, String requestGuard, BrandKitRejectInputDto input) {
        boolean another = input.getRequestAnother() == null || input.getRequestAnother();
        var taskId = requests.reject(brandId, input.getKitId(), input.getComment(), input.getPromotionId(), another);
        BrandKit rejected = kits.find(input.getKitId()).orElseThrow();
        BrandKitRejectionDto out = new BrandKitRejectionDto(toDto(rejected));
        out.setTaskId(taskId.orElse(null));
        return ResponseEntity.ok(out);
    }

    private static long id(BrandKit kit) { return java.util.Objects.requireNonNull(kit.id()); }

    private BrandKitVersionDto toDto(BrandKit k) {
        List<TemplatePreview> previews = kits.previews(k);
        List<BrandKitTemplateDto> templates = new java.util.ArrayList<>();
        for (int i = 0; i < k.kit().templates().size(); i++) templates.add(toDto(k.kit().templates().get(i), previews.get(i)));
        BrandKitVersionDto dto = new BrandKitVersionDto(id(k), k.version(), BrandKitVersionDto.StatusEnum.valueOf(k.status().name()),
                k.kit().palette().stream().map(c -> new BrandKitColorDto(c.name(), c.hex())).toList(), templates,
                k.createdAt().atOffset(ZoneOffset.UTC));
        dto.setNotes(k.kit().notes());
        dto.setComment(k.comment());
        dto.setSourceTaskId(k.sourceTaskId());
        dto.setApprovedAt(k.approvedAt() == null ? null : k.approvedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private static BrandKitTemplateDto toDto(KitTemplate t, TemplatePreview preview) {
        BrandKitTemplateDto dto = new BrandKitTemplateDto(t.id(), BrandKitTemplateDto.PieceEnum.fromValue(t.piece().code()), t.name(),
                t.svg(), preview.warnings(), preview.errors());
        if (t.image() != null) {
            dto.setImage(new BrandKitTemplateImageDto(t.image().x(), t.image().y(), t.image().width(), t.image().height()));
        }
        dto.setPreviewPngBase64(preview.pngBase64());
        return dto;
    }
}
