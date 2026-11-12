package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.InstagramApi;
import com.socialhub.api.model.InstagramAccountDto;
import com.socialhub.api.model.InstagramAccountInputDto;
import com.socialhub.api.model.InstagramBrandCheckDto;
import com.socialhub.api.model.InstagramCheckDto;
import com.socialhub.api.model.InstagramKeyInputDto;
import com.socialhub.api.model.InstagramStatusDto;
import com.socialhub.publishing.InstagramService;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class InstagramController implements InstagramApi {

    private final InstagramService instagram;

    @Override
    public ResponseEntity<InstagramStatusDto> getInstagram() {
        return ResponseEntity.ok(toDto(instagram.status()));
    }

    @Override
    public ResponseEntity<InstagramStatusDto> saveInstagramKey(String requestGuard, InstagramKeyInputDto input) {
        return ResponseEntity.ok(toDto(instagram.saveKey(input.getKey())));
    }

    @Override
    public ResponseEntity<Void> deleteInstagramKey(String requestGuard) {
        instagram.deleteKey();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InstagramCheckDto> checkInstagram(String requestGuard) {
        var check = instagram.check();
        var dto = new InstagramCheckDto(check.ok(), check.brands().stream().map(b -> {
            var item = new InstagramBrandCheckDto(b.brandId(), b.brandName());
            item.setUsername(b.username());
            item.setPublishingUsed(b.used());
            item.setPublishingTotal(b.total());
            item.setError(b.error());
            return item;
        }).toList());
        dto.setName(check.name());
        dto.setError(check.error());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<InstagramAccountDto> getBrandInstagramAccount(String brandId) {
        return ResponseEntity.ok(toDto(instagram.account(brandId)));
    }

    @Override
    public ResponseEntity<InstagramAccountDto> saveBrandInstagramAccount(String brandId, String requestGuard,
                                                                        InstagramAccountInputDto input) {
        return ResponseEntity.ok(toDto(instagram.saveAccount(brandId, input.getIgUserId())));
    }

    private static InstagramStatusDto toDto(InstagramService.Status status) {
        var dto = new InstagramStatusDto(status.configured());
        dto.setKeyHint(status.keyHint());
        return dto;
    }

    private static InstagramAccountDto toDto(Optional<InstagramAccountRepository.Account> account) {
        var dto = new InstagramAccountDto();
        account.ifPresent(a -> {
            dto.setIgUserId(a.igUserId());
            dto.setUsername(a.username());
        });
        return dto;
    }
}
