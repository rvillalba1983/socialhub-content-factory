package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.PubloraApi;
import com.socialhub.api.model.PlatformDto;
import com.socialhub.api.model.PubloraAccountDto;
import com.socialhub.api.model.PubloraAccountListDto;
import com.socialhub.api.model.PubloraAccountListInputDto;
import com.socialhub.api.model.PubloraConnectionDto;
import com.socialhub.api.model.PubloraDismissedInputDto;
import com.socialhub.api.model.PubloraKeyInputDto;
import com.socialhub.api.model.PubloraStatusDto;
import com.socialhub.publishing.PubloraService;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PubloraAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PubloraController implements PubloraApi {

    private final PubloraService publora;

    @Override
    public ResponseEntity<PubloraStatusDto> getPublora() {
        return ResponseEntity.ok(toDto(publora.status()));
    }

    @Override
    public ResponseEntity<PubloraStatusDto> savePubloraKey(String requestGuard, PubloraKeyInputDto input) {
        return ResponseEntity.ok(toDto(publora.saveKey(input.getKey())));
    }

    @Override
    public ResponseEntity<Void> deletePubloraKey(String requestGuard) {
        publora.deleteKey();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PubloraAccountListDto> getBrandPubloraAccounts(String brandId) {
        return ResponseEntity.ok(accountList(brandId));
    }

    @Override
    public ResponseEntity<PubloraAccountListDto> saveBrandPubloraAccounts(String brandId, String requestGuard,
                                                                         PubloraAccountListInputDto input) {
        var choices = input.getItems().stream()
                .map(i -> new PubloraService.Choice(Platform.valueOf(i.getPlatform().name()), i.getConnectionId()))
                .toList();
        publora.saveAccounts(brandId, choices);
        return ResponseEntity.ok(accountList(brandId));
    }

    @Override
    public ResponseEntity<PubloraAccountListDto> setBrandPubloraDismissed(String brandId, String requestGuard,
                                                                         PubloraDismissedInputDto input) {
        publora.setDismissed(brandId, input.getDismissed());
        return ResponseEntity.ok(accountList(brandId));
    }

    private PubloraAccountListDto accountList(String brandId) {
        return toDto(publora.accounts(brandId), publora.isDismissed(brandId));
    }

    private static PubloraStatusDto toDto(PubloraService.Status status) {
        var dto = new PubloraStatusDto(status.configured(), status.connections().stream()
                .map(c -> new PubloraConnectionDto(c.id(), c.platform(), c.label(), c.usable())).toList());
        dto.setKeyHint(status.keyHint());
        dto.setError(status.error());
        return dto;
    }

    private static PubloraAccountListDto toDto(List<PubloraAccountRepository.Account> accounts, boolean dismissed) {
        return new PubloraAccountListDto(accounts.stream()
                .map(a -> new PubloraAccountDto(PlatformDto.valueOf(a.platform().name()), a.connectionId(), a.label()))
                .toList(), dismissed);
    }
}
