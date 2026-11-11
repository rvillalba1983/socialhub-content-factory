# Siempre abierto · Mantiene abiertos Claude, OpenCode, Chrome y SocialHub, y que Windows no se duerma
# Cada minuto mira si cada programa está abierto y, si no, lo abre desde el menú Inicio.
# SocialHub se abre al iniciar sesión y se reabre si se cae, pero no si se ha cerrado con su botón «Cerrar»
# (eso se respeta hasta el siguiente inicio de sesión). Tampoco se toca mientras se instala un .msi.
# Mientras este script corre, Windows no entra en suspensión (la pantalla sí puede apagarse).
# Registro: %LOCALAPPDATA%\SiempreAbierto\registro.log
#
# -Once: una sola pasada (para probarlo), sin bucle y sin evitar la suspensión.
# -TestUserClose <registro>: solo dice si ese registro de SocialHub acaba en un cierre con «Cerrar» (para probarlo).

param([switch]$Once, [string]$TestUserClose)

$ErrorActionPreference = 'Continue'

# Programas: nombre en el menú Inicio (el primero que exista) y nombres de proceso que cuentan como «abierto».
# Si alguno no se encuentra, el registro lo dice; se ajusta aquí.
$apps = @(
  @{ Name = 'Claude';   Menu = @('Claude');                       Processes = @('claude') },
  @{ Name = 'OpenCode'; Menu = @('OpenCode', 'OpenCode Desktop');  Processes = @('OpenCode', 'opencode', 'opencode-desktop', 'OpenCode Desktop') },
  @{ Name = 'Chrome';   Menu = @('Google Chrome');                 Processes = @('chrome') },
  @{ Name = 'SocialHub'; Menu = @('SocialHub');                     Processes = @('SocialHub'); Url = 'http://127.0.0.1:8686/';
     Grace = 300; RespectUserClose = $true; WaitForInstaller = $true }
)
$intervalSeconds = 60   # cada cuánto se mira
$graceSeconds    = 120  # tras abrir un programa, cuánto se le deja arrancar antes de volver a intentarlo

$socialHubLog = Join-Path $env:LOCALAPPDATA 'SocialHub\logs\socialhub.log'

# ¿Lo último que dice el registro de SocialHub es que se cerró con «Cerrar»? (y no un arranque posterior)
function Test-ClosedByUser([string]$path) {
  if (-not (Test-Path $path)) { return $false }
  $last = Get-Content -Path $path -Tail 3000 -Encoding UTF8 -ErrorAction SilentlyContinue |
    Where-Object { $_ -match 'Started SocialHubApplication' -or $_ -match 'SocialHub se cierra a petici' } |
    Select-Object -Last 1
  return [bool]($last -and $last -match 'SocialHub se cierra a petici')
}

if ($TestUserClose) { if (Test-ClosedByUser $TestUserClose) { 'cerrado por Raul' } else { 'caido o sin cerrar' }; exit 0 }

$dir = Join-Path $env:LOCALAPPDATA 'SiempreAbierto'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$log = Join-Path $dir 'registro.log'

function Write-Log([string]$text) {
  $line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $text
  Write-Host $line
  try {
    if ((Test-Path $log) -and (Get-Item $log).Length -gt 1MB) { Move-Item $log "$log.1" -Force }
    Add-Content -Path $log -Value $line -Encoding UTF8
  } catch { }
}

# Una sola copia a la vez
$created = $false
$mutex = New-Object System.Threading.Mutex($true, 'Local\SiempreAbiertoSocialHub', [ref]$created)
if (-not $created) { Write-Log 'Ya hay otra copia en marcha: esta se cierra.'; exit 0 }

# Evitar la suspensión: ES_CONTINUOUS | ES_SYSTEM_REQUIRED. Al terminar el proceso, Windows vuelve a lo normal.
Add-Type -Namespace SiempreAbierto -Name Power -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);'
function Set-Awake([bool]$on) {
  $flags = if ($on) { [uint32]'0x80000001' } else { [uint32]'0x80000000' }
  [void][SiempreAbierto.Power]::SetThreadExecutionState($flags)
}

# Cómo abrir cada programa: primero por la lista de aplicaciones del menú Inicio; si no, por su acceso directo.
function Find-Launcher($app) {
  $startApps = @()
  try { $startApps = @(Get-StartApps) } catch { }
  foreach ($exact in @($true, $false)) {
    foreach ($name in $app.Menu) {
      $match = $startApps | Where-Object { if ($exact) { $_.Name -eq $name } else { $_.Name -like "$name*" } } | Select-Object -First 1
      if ($match) { return "shell:AppsFolder\$($match.AppID)" }
    }
  }
  $menus = @("$env:APPDATA\Microsoft\Windows\Start Menu\Programs", "$env:ProgramData\Microsoft\Windows\Start Menu\Programs")
  foreach ($name in $app.Menu) {
    foreach ($menu in $menus) {
      $lnk = Get-ChildItem -Path $menu -Filter "$name.lnk" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
      if ($lnk) { return $lnk.FullName }
    }
  }
  return $null
}

function Test-Running($app) {
  if (Get-Process -Name $app.Processes -ErrorAction SilentlyContinue) { return $true }
  if ($app.Url) {
    try { Invoke-WebRequest -Uri $app.Url -UseBasicParsing -TimeoutSec 5 | Out-Null; return $true }
    catch { if ($_.Exception.Response) { return $true } }
  }
  return $false
}

$lastLaunch  = @{}
$lastMissing = @{}
$firstPass   = $true
$userClosed  = @{}

function Invoke-Pass {
  foreach ($app in $apps) {
    if (Test-Running $app) { $userClosed.Remove($app.Name); continue }
    $grace = if ($app.Grace) { $app.Grace } else { $graceSeconds }
    $since = if ($lastLaunch.ContainsKey($app.Name)) { ((Get-Date) - $lastLaunch[$app.Name]).TotalSeconds } else { [double]::MaxValue }
    if ($since -lt $grace) { continue }
    if ($app.WaitForInstaller -and (Get-Process -Name 'msiexec' -ErrorAction SilentlyContinue | Where-Object { $_.SessionId -ne 0 })) { continue }
    # Al iniciar sesión se abre siempre; después, si se cerró con «Cerrar», se respeta
    if ($app.RespectUserClose -and -not $script:firstPass) {
      if ($userClosed.ContainsKey($app.Name)) { continue }
      if (Test-ClosedByUser $socialHubLog) {
        $userClosed[$app.Name] = $true
        Write-Log "$($app.Name) se ha cerrado con «Cerrar»: no se reabre hasta el siguiente inicio de sesión."
        continue
      }
      Write-Log "$($app.Name) se había caído (no se cerró con «Cerrar»)."
    }
    $launcher = Find-Launcher $app
    if (-not $launcher) {
      # Una vez por hora como mucho, para no llenar el registro
      $last = $lastMissing[$app.Name]
      if (-not $last -or ((Get-Date) - $last).TotalMinutes -ge 60) {
        Write-Log "No encuentro $($app.Name) en el menú Inicio (buscado: $($app.Menu -join ', '))."
        $lastMissing[$app.Name] = Get-Date
      }
      continue
    }
    try {
      if ($launcher.StartsWith('shell:')) { Start-Process -FilePath 'explorer.exe' -ArgumentList $launcher }
      else { Start-Process -FilePath $launcher }
      Write-Log "$($app.Name) estaba cerrado: abierto ($launcher)."
    } catch {
      Write-Log "No he podido abrir $($app.Name): $($_.Exception.Message)"
    }
    $lastLaunch[$app.Name] = Get-Date
  }
  $script:firstPass = $false
}

if ($Once) {
  Write-Log 'Pasada de prueba.'
  Invoke-Pass
  exit 0
}

Write-Log 'En marcha: Windows no se dormirá mientras siga abierto.'
try {
  while ($true) {
    Set-Awake $true
    Invoke-Pass
    Start-Sleep -Seconds $intervalSeconds
  }
} finally {
  Set-Awake $false
  Write-Log 'Parado.'
}
