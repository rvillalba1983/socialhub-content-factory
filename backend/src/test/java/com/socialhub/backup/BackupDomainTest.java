package com.socialhub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.backup.domain.BackupFile;
import com.socialhub.backup.domain.BackupKind;
import com.socialhub.backup.domain.BackupName;
import com.socialhub.backup.domain.BackupRotation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BackupDomainTest {

    @Test
    void namesCarryDateAndKindAndOnlyBackupFilesAreRecognised() {
        BackupName name = new BackupName(LocalDateTime.of(2026, 11, 8, 15, 0, 12), BackupKind.BEFORE_RESTORE);
        assertThat(name.value()).isEqualTo("socialhub-2026-11-08-150012-antes-de-restaurar.zip");
        assertThat(BackupName.parse(name.value())).contains(name);
        assertThat(BackupName.parse("socialhub-2026-11-08-150012-otra.zip")).isEmpty();
        assertThat(BackupName.parse("fotos.zip")).isEmpty();
        assertThat(BackupName.parse("../socialhub-2026-11-08-150012-diaria.zip")).isEmpty();
    }

    @Test
    void keepsTheFourteenMostRecent() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);
        List<BackupFile> files = IntStream.range(0, 17)
                .mapToObj(i -> new BackupFile("b" + i, start.plusDays(i), 10, BackupKind.DAILY, false)).toList();
        assertThat(BackupRotation.surplus(files)).extracting(BackupFile::name).containsExactlyInAnyOrder("b0", "b1", "b2");
        assertThat(BackupRotation.surplus(files.subList(0, 14))).isEmpty();
    }
}
