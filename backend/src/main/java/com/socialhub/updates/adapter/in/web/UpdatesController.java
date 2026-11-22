package com.socialhub.updates.adapter.in.web;

import com.socialhub.api.UpdatesApi;
import com.socialhub.api.model.UpdateStatusDto;
import com.socialhub.updates.Updates;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class UpdatesController implements UpdatesApi {

    private final Updates updates;

    @Override
    public ResponseEntity<UpdateStatusDto> getUpdateStatus() {
        Updates.Status s = updates.status();
        var dto = new UpdateStatusDto(s.installedVersion(), UpdateStatusDto.StateEnum.valueOf(s.state().name()), s.message());
        dto.setLatestVersion(s.latestVersion());
        dto.setInstallerUrl(s.installerUrl());
        dto.setReleaseUrl(s.releaseUrl());
        dto.setCheckedAt(s.checkedAt() == null ? null : s.checkedAt().atOffset(ZoneOffset.UTC));
        return ResponseEntity.ok(dto);
    }
}
