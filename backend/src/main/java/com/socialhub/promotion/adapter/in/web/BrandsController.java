package com.socialhub.promotion.adapter.in.web;

import com.socialhub.api.BrandsApi;
import com.socialhub.api.model.BrandDto;
import com.socialhub.api.model.BrandInputDto;
import com.socialhub.api.model.BrandListDto;
import com.socialhub.api.model.FolderListDto;
import com.socialhub.api.model.FolderListInputDto;
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
    public ResponseEntity<FolderListDto> replaceBrandFolders(String brandId, String requestGuard, FolderListInputDto input) {
        return ResponseEntity.ok(mapper.toFolderList(service.replaceBrandFolders(brandId, mapper.toFolderMap(input))));
    }
}
