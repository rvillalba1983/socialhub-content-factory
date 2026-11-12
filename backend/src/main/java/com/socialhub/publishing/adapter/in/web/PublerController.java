package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.PublerApi;
import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PublerAccountDto;
import com.socialhub.api.model.PublerAccountListDto;
import com.socialhub.api.model.PublerAccountListInputDto;
import com.socialhub.publishing.PublerAccountService;
import com.socialhub.publishing.domain.Platform;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PublerController implements PublerApi {

    private final PublerAccountService publer;

    @Override
    public ResponseEntity<PublerAccountListDto> getBrandPublerAccounts(String brandId) {
        return ResponseEntity.ok(toDto(publer.accounts(brandId)));
    }

    @Override
    public ResponseEntity<PublerAccountListDto> saveBrandPublerAccounts(String brandId, String requestGuard,
                                                                       PublerAccountListInputDto input) {
        var choices = input.getItems().stream()
                .map(i -> new PublerAccountService.Choice(Platform.valueOf(i.getPlatform().name()), i.getAccountName()))
                .toList();
        return ResponseEntity.ok(toDto(publer.save(brandId, choices)));
    }

    private static PublerAccountListDto toDto(Map<Platform, String> accounts) {
        return new PublerAccountListDto(accounts.entrySet().stream()
                .map(e -> new PublerAccountDto(PlatformDto.valueOf(e.getKey().name()), e.getValue())).toList());
    }
}
