# Siempre abierto · Desinstalación
# Para el script, quita el acceso directo de «Inicio» y Windows vuelve a dormirse como siempre.
# Deja el registro en %LOCALAPPDATA%\SiempreAbierto. No cierra Claude, OpenCode, Chrome ni SocialHub.

$ErrorActionPreference = 'Continue'
$link = Join-Path ([Environment]::GetFolderPath('Startup')) 'Siempre abierto (Claude, OpenCode y Chrome).lnk'

$running = Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe' OR Name = 'pwsh.exe'" |
  Where-Object { $_.CommandLine -like '*mantener-abiertos.ps1*' }
foreach ($p in $running) {
  Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
  Write-Host "Parado (proceso $($p.ProcessId))."
}

if (Test-Path $link) { Remove-Item $link -Force; Write-Host 'Quitado del inicio de sesión.' }
else { Write-Host 'No estaba en el inicio de sesión.' }
Write-Host 'Windows vuelve a dormirse como siempre.'
