package com.socialhub.launcher.adapter.out.windows;

import com.socialhub.launcher.domain.AutostartEntry;
import com.socialhub.platform.desktop.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * La entrada {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run\SocialHub}: es del usuario, no pide administrador ni
 * toca nada del sistema. Se escribe con PowerShell (orden codificada, así no hay problemas de comillas) y sin ventana.
 * Solo existe con SocialHub instalado (el programa en marcha es {@code SocialHub.exe}); desde el IDE o con {@code java -jar}
 * no hace nada. Arranca con {@code --background}: sin abrir el navegador.
 */
@Slf4j
@Component
class PowerShellAutostart implements AutostartEntry {

    static final String KEY = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String NAME = "SocialHub";
    static final String BACKGROUND = "--background";
    private static final long TIMEOUT_SECONDS = 20;

    @Override
    public boolean supported() {
        return Desktop.enabled() && windows() && executable() != null;
    }

    @Override
    public boolean exists() {
        if (!supported()) return false;
        String out = run(script("(Get-ItemProperty -Path '" + KEY + "' -Name '" + NAME + "' -ErrorAction SilentlyContinue).'" + NAME + "'"));
        return out != null && !out.isBlank();
    }

    @Override
    public void register() {
        String exe = executable();
        if (!supported() || exe == null) return;
        run(script(setScript(exe)));
    }

    @Override
    public void unregister() {
        if (!supported()) return;
        run(script("Remove-ItemProperty -Path '" + KEY + "' -Name '" + NAME + "' -ErrorAction SilentlyContinue"));
    }

    // ---------------------------------------------------------------- Ayudas (visibles para las pruebas)

    /** El valor de la entrada: el programa entre comillas y la marca de segundo plano. */
    static String command(String exe) { return "\"" + exe + "\" " + BACKGROUND; }

    static String setScript(String exe) {
        return "Set-ItemProperty -Path '" + KEY + "' -Name '" + NAME + "' -Value '" + command(exe).replace("'", "''") + "' -Type String";
    }

    /** La orden para {@code powershell -EncodedCommand}: UTF-16 little endian en Base64. */
    static String encode(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    static List<String> script(String script) {
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encode(script));
    }

    private static boolean windows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }

    /** El {@code SocialHub.exe} en marcha (el lanzador de jpackage), o null si se ejecuta de otra forma. */
    private static String executable() {
        return ProcessHandle.current().info().command()
                .filter(c -> Path.of(c).getFileName().toString().equalsIgnoreCase("SocialHub.exe")).orElse(null);
    }

    private static String run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("PowerShell no respondió");
            }
            if (process.exitValue() != 0) throw new IllegalStateException("PowerShell terminó con " + process.exitValue() + ": " + out);
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar PowerShell: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido");
        }
    }
}
