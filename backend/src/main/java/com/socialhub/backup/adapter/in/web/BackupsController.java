package com.socialhub.backup.adapter.in.web;

import com.socialhub.api.BackupsApi;
import com.socialhub.api.model.BackupDto;
import com.socialhub.api.model.BackupFolderInputDto;
import com.socialhub.api.model.BackupListDto;
import com.socialhub.api.model.RestorationDto;
import com.socialhub.api.model.RestorationInputDto;
import com.socialhub.backup.BackupService;
import com.socialhub.backup.domain.BackupFile;
import com.socialhub.backup.domain.BackupKind;
import com.socialhub.backup.domain.BackupRotation;
import java.time.Clock;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BackupsController implements BackupsApi {

    private final BackupService backups;
    private final Clock clock;

    @Override
    public ResponseEntity<BackupListDto> getBackups() {
        return ResponseEntity.ok(list());
    }

    @Override
    public ResponseEntity<BackupDto> createBackup(String requestGuard) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(backups.create(BackupKind.MANUAL)));
    }

    @Override
    public ResponseEntity<BackupListDto> saveBackupFolder(String requestGuard, BackupFolderInputDto input) {
        backups.chooseFolder(input.getFolder());
        return ResponseEntity.ok(list());
    }

    @Override
    public ResponseEntity<RestorationDto> restoreBackup(String requestGuard, RestorationInputDto input) {
        BackupService.Restored r = backups.restore(input.getName(), input.getConfirmation());
        return ResponseEntity.ok(new RestorationDto(r.restored(), r.safetyBackup(), r.haltedReason()));
    }

    private BackupListDto list() {
        BackupService.Folders f = backups.folders();
        BackupListDto dto = new BackupListDto(f.chosenAvailable(), f.local().toString(), BackupRotation.KEEP,
                backups.list().stream().map(this::dto).toList());
        if (f.chosen() != null) dto.setFolder(f.chosen().toString());
        return dto;
    }

    private BackupDto dto(BackupFile b) {
        ZoneOffset offset = clock.getZone().getRules().getOffset(b.createdAt());
        return new BackupDto(b.name(), b.createdAt().atOffset(offset), b.sizeBytes(),
                BackupDto.KindEnum.valueOf(b.kind().name()), b.local());
    }
}
