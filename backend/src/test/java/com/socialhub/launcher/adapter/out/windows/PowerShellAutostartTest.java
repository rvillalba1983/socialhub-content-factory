package com.socialhub.launcher.adapter.out.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** La entrada de arranque: valor con el programa entre comillas y {@code --background}, y la orden de PowerShell sin problemas de comillas. */
class PowerShellAutostartTest {

    @Test
    void theValueIsTheQuotedProgramWithTheBackgroundFlag() {
        assertThat(PowerShellAutostart.command("C:\\Program Files\\SocialHub\\SocialHub.exe"))
                .isEqualTo("\"C:\\Program Files\\SocialHub\\SocialHub.exe\" --background");
    }

    @Test
    void theScriptWritesOnlyTheUsersRunKeyAndEscapesSingleQuotes() {
        String script = PowerShellAutostart.setScript("C:\\Users\\Raúl O'Neil\\SocialHub.exe");
        assertThat(script).startsWith("Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name 'SocialHub'");
        assertThat(script).doesNotContain("HKLM");
        assertThat(script).contains("O''Neil").contains("--background");
    }

    @Test
    void theCommandTravelsEncodedSoQuotesNeverBreak() {
        var command = PowerShellAutostart.script("Write-Output 'a \"b\"'");
        assertThat(command).startsWith("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden");
        assertThat(command.getLast()).isEqualTo(PowerShellAutostart.encode("Write-Output 'a \"b\"'"));
        assertThat(new String(Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE)).isEqualTo("Write-Output 'a \"b\"'");
    }

    @Test
    void outsideTheInstalledProgramItDoesNothing() {
        // Desde el IDE o con java -jar no hay SocialHub.exe ni modo de escritorio: no se toca el registro
        var autostart = new PowerShellAutostart();
        assertThat(autostart.supported()).isFalse();
        assertThat(autostart.exists()).isFalse();
        autostart.register();
        autostart.unregister();
    }
}
