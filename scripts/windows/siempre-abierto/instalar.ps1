# Siempre abierto · Instalación
# Copia mantener-abiertos.ps1 a %LOCALAPPDATA%\SiempreAbierto, crea un acceso directo en la carpeta «Inicio»
# del usuario (arranca al iniciar sesión, sin ventana) y lo pone en marcha ya. No necesita permisos de administrador.
# -NoStart: instala sin ponerlo en marcha (para pruebas).

param([switch]$NoStart)

$ErrorActionPreference = 'Stop'
$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$dir    = Join-Path $env:LOCALAPPDATA 'SiempreAbierto'
$script = Join-Path $dir 'mantener-abiertos.ps1'
$link   = Join-Path ([Environment]::GetFolderPath('Startup')) 'Siempre abierto (Claude, OpenCode y Chrome).lnk'
$args_  = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""

New-Item -ItemType Directory -Force -Path $dir | Out-Null
Copy-Item -Path (Join-Path $here 'mantener-abiertos.ps1') -Destination $script -Force
Write-Host "Script copiado en $script"

$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut($link)
$lnk.TargetPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$lnk.Arguments = $args_
$lnk.WorkingDirectory = $dir
$lnk.WindowStyle = 7
$lnk.Description = 'Mantiene abiertos Claude, OpenCode y Chrome, y evita que Windows se duerma'
$lnk.Save()
Write-Host "Arrancará solo al iniciar sesión: $link"

if (-not $NoStart) {
  Start-Process -FilePath $lnk.TargetPath -ArgumentList $args_ -WindowStyle Hidden
  Write-Host 'En marcha. Registro: ' (Join-Path $dir 'registro.log')
}
