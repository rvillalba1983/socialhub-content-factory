package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.BrandsApi;
import com.socialhub.api.model.BrandDto;
import com.socialhub.api.model.BrandInputDto;
import com.socialhub.api.model.BrandListDto;
import com.socialhub.api.model.FolderCreationInputDto;
import com.socialhub.api.model.FolderListDto;
import com.socialhub.api.model.PublicShareDto;
import com.socialhub.api.model.PublicShareInputDto;
import com.socialhub.api.model.FolderListInputDto;
import com.socialhub.api.model.BrandIdentityDto;
import com.socialhub.api.model.EditorialDecisionDto;
import com.socialhub.api.model.EditorialDecisionInputDto;
import com.socialhub.api.model.EditorialDecisionListDto;
import com.socialhub.promotion.domain.EditorialDecision;
import java.time.ZoneOffset;
import com.socialhub.promotion.domain.BrandIdentity;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.promotion.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BrandsController implements BrandsApi {

    private final PromotionQueries queries;
    private final PromotionService service;
    private final PromotionWebMapper mapper;

    @Override
    public ResponseEntity<BrandListDto> listBrands() {
        return ResponseEntity.ok(new BrandListDto(mapper.toBrandDtos(queries.brands())));
    }

    @Override
    public ResponseEntity<BrandDto> createBrand(String requestGuard, BrandInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(service.createBrand(mapper.toDraft(input))));
    }

    @Override
    public ResponseEntity<BrandDto> updateBrand(String brandId, String requestGuard, BrandInputDto input) {
        return ResponseEntity.ok(mapper.toDto(service.updateBrand(brandId, mapper.toDraft(input))));
    }

    @Override
    public ResponseEntity<FolderListDto> listBrandFolders(String brandId) {
        return ResponseEntity.ok(mapper.toFolderList(queries.brandFolders(brandId)));
    }

    @Override
    public ResponseEntity<FolderListDto> createBrandFolders(String brandId, String requestGuard, FolderCreationInputDto input) {
        return ResponseEntity.ok(mapper.toFolderList(service.createBrandFolders(brandId, input.getBase())));
    }

    @Override
    public ResponseEntity<FolderListDto> replaceBrandFolders(String brandId, String requestGuard, FolderListInputDto input) {
        return ResponseEntity.ok(mapper.toFolderList(service.replaceBrandFolders(brandId, mapper.toFolderMap(input))));
    }

    @Override
    public ResponseEntity<PublicShareDto> getBrandPublicShare(String brandId) {
        return ResponseEntity.ok(new PublicShareDto(service.publicShared(brandId)));
    }

    @Override
    public ResponseEntity<PublicShareDto> setBrandPublicShare(String brandId, String requestGuard, PublicShareInputDto input) {
        return ResponseEntity.ok(new PublicShareDto(service.setPublicShared(brandId, input.getShared())));
    }

    @Override
    public ResponseEntity<BrandIdentityDto> getBrandIdentity(String brandId) {
        return ResponseEntity.ok(toDto(queries.identity(brandId)));
    }

    @Override
    public ResponseEntity<BrandIdentityDto> saveBrandIdentity(String brandId, String requestGuard, BrandIdentityDto in) {
        BrandIdentity identity = new BrandIdentity(in.getTone(), in.getAudience(), in.getForbiddenTopics(),
                in.getPreferredExpressions(), in.getForbiddenExpressions(), in.getColors(), in.getFont(), in.getLogo(),
                in.getVisualStyle(), in.getPhotoStyle(), in.getCallToAction(), in.getFixedHashtags());
        return ResponseEntity.ok(toDto(service.saveIdentity(brandId, identity)));
    }

    private static BrandIdentityDto toDto(BrandIdentity i) {
        return new BrandIdentityDto(i.tone(), i.audience(), i.forbiddenTopics(), i.preferredExpressions(),
                i.forbiddenExpressions(), i.colors(), i.font(), i.logo(), i.visualStyle(), i.photoStyle(),
                i.callToAction(), i.fixedHashtags());
    }

    @Override
    public ResponseEntity<EditorialDecisionListDto> listBrandDecisions(String brandId) {
        return ResponseEntity.ok(new EditorialDecisionListDto(queries.decisions(brandId).stream().map(BrandsController::toDto).toList()));
    }

    @Override
    public ResponseEntity<EditorialDecisionDto> addBrandDecision(String brandId, String requestGuard, EditorialDecisionInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(service.addDecision(brandId, input.getText())));
    }

    @Override
    public ResponseEntity<Void> deleteBrandDecision(String brandId, Long decisionId, String requestGuard) {
        service.deleteDecision(brandId, decisionId);
        return ResponseEntity.noContent().build();
    }

    private static EditorialDecisionDto toDto(EditorialDecision d) {
        return new EditorialDecisionDto(d.id(), d.text(), d.createdAt().atOffset(ZoneOffset.UTC));
    }
}
