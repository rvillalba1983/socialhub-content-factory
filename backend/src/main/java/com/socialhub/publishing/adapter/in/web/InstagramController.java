package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.InstagramApi;
import com.socialhub.api.model.InstagramAccountDto;
import com.socialhub.api.model.InstagramAppInputDto;
import com.socialhub.api.model.InstagramConnectionDto;
import com.socialhub.api.model.InstagramDetectedAccountDto;
import com.socialhub.api.model.InstagramDetectedAccountListDto;
import com.socialhub.api.model.InstagramOAuthStartDto;
import com.socialhub.api.model.InstagramAccountInputDto;
import com.socialhub.api.model.InstagramBrandCheckDto;
import com.socialhub.api.model.InstagramCheckDto;
import com.socialhub.api.model.InstagramKeyInputDto;
import com.socialhub.api.model.InstagramStatusDto;
import com.socialhub.publishing.InstagramOAuthService;
import com.socialhub.publishing.InstagramService;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class InstagramController implements InstagramApi {

    private final InstagramService instagram;
    private final InstagramOAuthService oauth;

    @Override
    public ResponseEntity<InstagramStatusDto> getInstagram() {
        return ResponseEntity.ok(toDto(instagram.status(), oauth.overview()));
    }

    @Override
    public ResponseEntity<InstagramStatusDto> saveInstagramKey(String requestGuard, InstagramKeyInputDto input) {
        instagram.saveKey(input.getKey());
        return ResponseEntity.ok(toDto(instagram.status(), oauth.overview()));
    }

    @Override
    public ResponseEntity<Void> deleteInstagramKey(String requestGuard) {
        instagram.deleteKey();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InstagramStatusDto> saveInstagramApp(String requestGuard, InstagramAppInputDto input) {
        oauth.saveApp(input.getAppId(), input.getAppSecret());
        return ResponseEntity.ok(toDto(instagram.status(), oauth.overview()));
    }

    @Override
    public ResponseEntity<InstagramOAuthStartDto> startInstagramOAuth(String requestGuard) {
        return ResponseEntity.ok(new InstagramOAuthStartDto(oauth.start()));
    }

    @Override
    public ResponseEntity<Void> instagramOAuthCallback(String code, String state, String error, String errorDescription) {
        var result = oauth.callback(code, state, error, errorDescription);
        String target = "/?instagram=" + (result.ok() ? "ok" : "error") + "&message="
                + URLEncoder.encode(result.message(), StandardCharsets.UTF_8);
        return ResponseEntity.status(302).location(URI.create(target)).header("Cache-Control", "no-store").build();
    }

    @Override
    public ResponseEntity<Void> disconnectInstagramOAuth(String requestGuard) {
        oauth.disconnect();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InstagramDetectedAccountListDto> listInstagramAccounts() {
        return ResponseEntity.ok(new InstagramDetectedAccountListDto(oauth.detected().stream().map(d -> {
            var dto = new InstagramDetectedAccountDto(d.igUserId(), d.username(), d.pageName());
            dto.setBrandId(d.brandId());
            return dto;
        }).toList()));
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

    private static InstagramStatusDto toDto(InstagramService.Status system, InstagramOAuthService.Overview overview) {
        var dto = new InstagramStatusDto(!"NONE".equals(overview.source()));
        dto.setKeyHint(system.keyHint());
        dto.setSystemKey(system.configured());
        dto.setSource(InstagramStatusDto.SourceEnum.valueOf(overview.source()));
        dto.setAppId(overview.app().appId());
        dto.setAppSecretSaved(overview.app().secretSaved());
        dto.setRedirectUri(overview.app().redirectUri());
        var c = overview.connection();
        if (c != null) {
            var connection = new InstagramConnectionDto(c.usernames(), c.expiresAt().atOffset(ZoneOffset.UTC), (int) c.daysLeft(), c.expired());
            connection.setName(c.name());
            dto.setConnection(connection);
        }
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
