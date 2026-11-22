# Instala y desinstala el .msi CON su interfaz (como el doble clic de Raul), maneja las ventanas con UI Automation
# y guarda capturas de pantalla de cada paso. Es informativo: lo llama un paso del workflow con continue-on-error.
param([Parameter(Mandatory)][string]$Msi, [Parameter(Mandatory)][string]$Salida)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Windows.Forms, System.Drawing
New-Item -ItemType Directory -Force $Salida | Out-Null
function Captura([string]$nombre) {
  $b = [System.Windows.Forms.SystemInformation]::VirtualScreen
  $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
  $g = [System.Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($b.Left, $b.Top, 0, 0, $bmp.Size)
  $bmp.Save((Join-Path $Salida "$nombre.png"), [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
}
function Esperar-Boton([string]$nombre, [int]$seg) {
  $cond = New-Object System.Windows.Automation.PropertyCondition ([System.Windows.Automation.AutomationElement]::NameProperty), $nombre
  for ($i = 0; $i -lt $seg; $i++) {
    $e = [System.Windows.Automation.AutomationElement]::RootElement.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
    if ($e) { return $e }
    Start-Sleep 1
  }
  return $null
}
function Textos($boton) {
  $v = [System.Windows.Automation.TreeWalker]::ControlViewWalker
  $w = $boton; while ($w -and $w.Current.ControlType.ProgrammaticName -ne 'ControlType.Window') { $w = $v.GetParent($w) }
  if (-not $w) { return @() }
  $todos = $w.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)
  @("Ventana: $($w.Current.Name)") + @($todos | ForEach-Object { "$($_.Current.ControlType.ProgrammaticName.Replace('ControlType.', '')): $($_.Current.Name)" } | Where-Object { $_ -notmatch ': $' })
}
function Mostrar-Respuesta { try { Invoke-RestMethod http://localhost:8686/api/system/version -TimeoutSec 3 } catch { $null } }
function Cerrar-App {
  try { Invoke-WebRequest http://localhost:8686/api/app/shutdown -Method Post -Headers @{ 'X-SocialHub-Request' = '1' } -UseBasicParsing | Out-Null } catch {}
  for ($i = 0; $i -lt 30 -and (Get-Process SocialHub -ErrorAction SilentlyContinue); $i++) { Start-Sleep 2 }
}

# 1) Instalar con interfaz
$p = Start-Process msiexec.exe -ArgumentList "/i `"$Msi`"" -PassThru
Start-Sleep 5; Captura '01-instalando'
$fin = Esperar-Boton 'Finalizar' 180
Captura '02-pantalla-final-instalacion'
if (-not $fin) { throw 'No apareció la pantalla final con el botón Finalizar' }
$t = Textos $fin; $t | Tee-Object (Join-Path $Salida 'textos-instalacion.txt')
if (($t -join "`n") -notmatch 'SocialHub se ha instalado correctamente') { throw 'La pantalla final no dice «SocialHub se ha instalado correctamente»' }
if (($t -join "`n") -notmatch 'Abrir SocialHub ahora') { throw 'No está la casilla «Abrir SocialHub ahora»' }
$casilla = ($t | Where-Object { $_ -match 'Abrir SocialHub ahora' })
$cond = New-Object System.Windows.Automation.PropertyCondition ([System.Windows.Automation.AutomationElement]::NameProperty), 'Abrir SocialHub ahora'
$ce = [System.Windows.Automation.AutomationElement]::RootElement.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
$estado = $ce.GetCurrentPattern([System.Windows.Automation.TogglePattern]::Pattern).Current.ToggleState
Write-Host "Casilla: $estado"
if ($estado -ne 'On') { throw "La casilla no viene marcada (estado $estado)" }
$fin.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
$v = $null
for ($i = 0; $i -lt 90 -and -not $v; $i++) { Start-Sleep 2; $v = Mostrar-Respuesta }
if (-not $v) { throw 'Con la casilla marcada, Finalizar no abrió SocialHub (no responde en localhost:8686)' }
Write-Host "Finalizar abrió SocialHub: versión $($v.version)"
$p.WaitForExit(60000) | Out-Null
Cerrar-App

# 2) Misma versión otra vez (mantenimiento)
$p = Start-Process msiexec.exe -ArgumentList "/i `"$Msi`"" -PassThru
$fin = Esperar-Boton 'Finalizar' 120; Captura '03-pantalla-final-mantenimiento'
if ($fin) { (Textos $fin) | Tee-Object (Join-Path $Salida 'textos-mantenimiento.txt'); $fin.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke() }
$p.WaitForExit(60000) | Out-Null
if (Get-Process SocialHub -ErrorAction SilentlyContinue) { throw 'En mantenimiento no tenía que abrirse SocialHub' }

# 3) Desinstalar con interfaz
$p = Start-Process msiexec.exe -ArgumentList "/x `"$Msi`"" -PassThru
# Windows pregunta primero, con su propio cuadro, si se quiere desinstalar (sale en el idioma de Windows)
$si = Esperar-Boton 'Yes' 30; if (-not $si) { $si = Esperar-Boton 'Sí' 5 }
if ($si) { Captura '04a-confirmacion-de-windows'; $si.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke() }
$fin = Esperar-Boton 'Finalizar' 180
Captura '04-pantalla-final-desinstalacion'
if (-not $fin) { throw 'No apareció la pantalla final al desinstalar' }
$t = Textos $fin; $t | Tee-Object (Join-Path $Salida 'textos-desinstalacion.txt')
if (($t -join "`n") -notmatch 'Se ha desinstalado SocialHub') { throw 'La pantalla final de desinstalar no es la esperada' }
if (($t -join "`n") -match 'Abrir SocialHub ahora') { throw 'Al desinstalar no tenía que haber casilla de abrir' }
$fin.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
$p.WaitForExit(60000) | Out-Null
if (Test-Path (Join-Path $env:ProgramFiles 'SocialHub\SocialHub.exe')) { throw 'No se desinstaló' }
Write-Host 'Interfaz probada: instalar (con abrir), mantenimiento y desinstalar'
