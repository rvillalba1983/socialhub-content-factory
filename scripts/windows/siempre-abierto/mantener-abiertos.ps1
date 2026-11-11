# Siempre abierto · Mantiene abiertos Claude, OpenCode y Chrome, y que Windows no se duerma
# Cada minuto mira si cada programa está abierto y, si no, lo abre desde el menú Inicio.
# Mientras este script corre, Windows no entra en suspensión (la pantalla sí puede apagarse).
# Registro: %LOCALAPPDATA%\SiempreAbierto\registro.log
#
# -Once: una sola pasada (para probarlo), sin bucle y sin evitar la suspensión.

param([switch]$Once)

$ErrorActionPreference = 'Continue'

# Programas: nombre en el menú Inicio (el primero que exista) y nombres de proceso que cuentan como «abierto».
# Si alguno no se encuentra, el registro lo dice; se ajusta aquí.
$apps = @(
  @{ Name = 'Claude';   Menu = @('Claude');                       Processes = @('claude') },
  @{ Name = 'OpenCode'; Menu = @('OpenCode', 'OpenCode Desktop');  Processes = @('OpenCode', 'opencode', 'opencode-desktop', 'OpenCode Desktop') },
  @{ Name = 'Chrome';   Menu = @('Google Chrome');                 Processes = @('chrome') }
)
$intervalSeconds = 60   # cada cuánto se mira
$graceSeconds    = 120  # tras abrir un programa, cuánto se le deja arrancar antes de volver a intentarlo

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
  return [bool](Get-Process -Name $app.Processes -ErrorAction SilentlyContinue)
}

$lastLaunch  = @{}
$lastMissing = @{}

function Invoke-Pass {
  foreach ($app in $apps) {
    if (Test-Running $app) { continue }
    $since = if ($lastLaunch.ContainsKey($app.Name)) { ((Get-Date) - $lastLaunch[$app.Name]).TotalSeconds } else { [double]::MaxValue }
    if ($since -lt $graceSeconds) { continue }
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
