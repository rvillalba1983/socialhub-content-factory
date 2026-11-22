# Comprueba la interfaz del .msi leyendo sus tablas con la API de Windows Installer (nadie ve la pantalla en la CI).
# Falla (codigo 1) si falta la pantalla final (ExitDialog), si hay pasos de asistente que nadie pidio, si la accion de
# "Abrir SocialHub ahora" no es la conservadora o si se ha roto algo de lo de antes (carpeta, accesos directos, upgrade-uuid...).
# Uso: pwsh -File comprobar-msi.ps1 -Msi SocialHub-1.2.3.msi -Version 1.2.3 -UpgradeCode cb398502-... [-Informe informe.txt]
param(
  [Parameter(Mandatory)][string]$Msi,
  [Parameter(Mandatory)][string]$Version,
  [Parameter(Mandatory)][string]$UpgradeCode,
  [string]$Informe
)
$ErrorActionPreference = 'Stop'
$Msi = (Resolve-Path $Msi).Path
$salida = New-Object System.Collections.Generic.List[string]
function Escribir([string]$t) { Write-Host $t; $salida.Add($t) }
$fallos = New-Object System.Collections.Generic.List[string]
function Exigir([bool]$cond, [string]$ok, [string]$mal) {
  if ($cond) { Escribir "  OK    $ok" } else { Escribir "  FALLA $mal"; $fallos.Add($mal) }
}

$com = [System.__ComObject]
function Llamar($obj, [string]$nombre, $argumentos) {
  $com.InvokeMember($nombre, [Reflection.BindingFlags]::InvokeMethod, $null, $obj, $argumentos)
}
function Propiedad($obj, [string]$nombre, $argumentos) {
  $com.InvokeMember($nombre, [Reflection.BindingFlags]::GetProperty, $null, $obj, $argumentos)
}
$instalador = New-Object -ComObject WindowsInstaller.Installer
$db = Llamar $instalador 'OpenDatabase' @($Msi, 0)   # 0 = solo lectura

# Devuelve las filas como listas de cadenas (una por columna)
function Consultar([string]$sql) {
  $vista = Llamar $db 'OpenView' @($sql)
  [void](Llamar $vista 'Execute' $null)
  $filas = New-Object System.Collections.Generic.List[object]
  while ($true) {
    $r = Llamar $vista 'Fetch' $null
    if ($null -eq $r) { break }
    $n = Propiedad $r 'FieldCount' $null
    $cols = @(for ($i = 1; $i -le $n; $i++) { [string](Propiedad $r 'StringData' @($i)) })
    $filas.Add($cols)
  }
  [void](Llamar $vista 'Close' $null)
  $filas.ToArray()
}
function Tabla([string]$nombre) { @(Consultar "SELECT * FROM ``$nombre``") }

Escribir "== Interfaz de $(Split-Path $Msi -Leaf) =="

# --- Propiedades
$props = @{}
foreach ($f in (Consultar 'SELECT `Property`, `Value` FROM `Property`')) { $props[$f[0]] = $f[1] }
Escribir "Propiedades de SocialHub:"
foreach ($k in ($props.Keys | Where-Object { $_ -like 'SH_*' -or $_ -like 'WIXUI_*' -or $_ -in 'ProductName', 'ProductVersion', 'ProductLanguage', 'DefaultUIFont' } | Sort-Object)) { Escribir "  $k = $($props[$k])" }
Exigir ($props['ProductName'] -eq 'SocialHub') 'ProductName = SocialHub' "ProductName es '$($props['ProductName'])'"
Exigir ($props['ProductVersion'] -eq $Version) "ProductVersion = $Version" "ProductVersion es '$($props['ProductVersion'])' y tendria que ser $Version"
Exigir ($props['ProductLanguage'] -eq '1033') 'ProductLanguage sigue en 1033 (no cambia la identidad del producto)' "ProductLanguage es '$($props['ProductLanguage'])'"
Exigir ($props['WIXUI_EXITDIALOGOPTIONALCHECKBOX'] -eq '1') 'La casilla de abrir SocialHub viene marcada (WIXUI_EXITDIALOGOPTIONALCHECKBOX = 1)' 'La casilla de abrir SocialHub no viene marcada por defecto'
Exigir ($props['WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT'] -eq 'Abrir SocialHub ahora') 'Texto de la casilla: «Abrir SocialHub ahora»' "Texto de la casilla: '$($props['WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT'])'"
Exigir ($props['SH_TITULO'] -eq 'SocialHub se ha instalado correctamente') 'Titulo final: «SocialHub se ha instalado correctamente»' "SH_TITULO es '$($props['SH_TITULO'])'"

# --- Dialogos
$dialogos = @(Consultar 'SELECT `Dialog` FROM `Dialog`' | ForEach-Object { $_[0] })
Escribir "Dialogos: $($dialogos -join ', ')"
Exigir ($dialogos -contains 'ExitDialog') 'Existe ExitDialog (pantalla final)' 'FALTA ExitDialog (la pantalla final)'
Exigir ($dialogos -contains 'ProgressDlg') 'Existe ProgressDlg (barra de progreso)' 'FALTA ProgressDlg'
$extra = @($dialogos | Where-Object { $_ -in 'WelcomeDlg', 'WelcomeEulaDlg', 'LicenseAgreementDlg', 'InstallDirDlg', 'ShortcutPromptDlg', 'InstallDirNotEmptyDlg', 'VerifyReadyDlg', 'MaintenanceWelcomeDlg', 'MaintenanceTypeDlg' })
Exigir ($extra.Count -eq 0) 'Sin pantallas de asistente antes de instalar (ni bienvenida, ni licencia, ni carpeta, ni accesos directos)' "Hay pantallas de asistente que nadie pidio: $($extra -join ', ')"
Exigir (-not ($dialogos -contains 'FatalError') -and -not ($dialogos -contains 'ErrorDlg') -and -not ($dialogos -contains 'UserExit')) 'Sin dialogos de error propios: los errores los enseña Windows (mensaje estandar)' 'Hay un dialogo de error propio (FatalError, ErrorDlg o UserExit)'

# --- Controles de la pantalla final
$controles = @(Consultar 'SELECT `Control`, `Type`, `Property`, `Text` FROM `Control` WHERE `Dialog_`=''ExitDialog''')
Escribir "Controles de ExitDialog: $(($controles | ForEach-Object { $_[0] }) -join ', ')"
$titulo = $controles | Where-Object { $_[0] -eq 'Title' } | Select-Object -First 1
$desc = $controles | Where-Object { $_[0] -eq 'Description' } | Select-Object -First 1
$casilla = $controles | Where-Object { $_[0] -eq 'OptionalCheckBox' } | Select-Object -First 1
$fin = $controles | Where-Object { $_[0] -eq 'Finish' } | Select-Object -First 1
Exigir ($null -ne $fin) 'ExitDialog tiene el boton Finish' 'ExitDialog no tiene el boton Finish'
Exigir ($null -ne $casilla -and $casilla[2] -eq 'WIXUI_EXITDIALOGOPTIONALCHECKBOX' -and $casilla[3] -eq '[WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT]') 'ExitDialog tiene la casilla enlazada a WIXUI_EXITDIALOGOPTIONALCHECKBOX' 'ExitDialog no tiene la casilla opcional enlazada'
Exigir ($null -ne $titulo -and $titulo[3] -like '*[[]SH_TITULO]*') 'El titulo de ExitDialog usa [SH_TITULO]' "El titulo de ExitDialog es '$($titulo[3])'"
Exigir ($null -ne $desc -and $desc[3] -eq '[SH_DESCRIPCION]') 'La descripcion de ExitDialog usa [SH_DESCRIPCION]' "La descripcion de ExitDialog es '$($desc[3])'"
$finTxt = if ($fin) { $fin[3] } else { '' }
Exigir ($finTxt -like '*Finalizar') 'El boton se llama «Finalizar»' "El boton Finish dice '$finTxt'"

# --- Eventos de control: al pulsar Finalizar, abrir SocialHub (si la casilla esta marcada) y cerrar
$eventos = @(Consultar 'SELECT `Control_`, `Event`, `Argument`, `Condition`, `Ordering` FROM `ControlEvent` WHERE `Dialog_`=''ExitDialog''')
foreach ($e in $eventos) { Escribir "  ControlEvent ExitDialog/$($e[0]): $($e[1]) $($e[2]) [orden $($e[4])] si $($e[3])" }
$abrir = $eventos | Where-Object { $_[0] -eq 'Finish' -and $_[1] -eq 'DoAction' -and $_[2] -eq 'SH_AbrirSocialHub' } | Select-Object -First 1
$cerrar = $eventos | Where-Object { $_[0] -eq 'Finish' -and $_[1] -eq 'EndDialog' -and $_[2] -eq 'Return' } | Select-Object -First 1
Exigir ($null -ne $abrir) 'Finalizar ejecuta la accion SH_AbrirSocialHub' 'Finalizar no ejecuta SH_AbrirSocialHub'
Exigir ($null -ne $abrir -and $abrir[3] -match 'WIXUI_EXITDIALOGOPTIONALCHECKBOX\s*=\s*1' -and $abrir[3] -match 'NOT Installed') 'Solo se abre con la casilla marcada y tras instalar (no al desinstalar ni en mantenimiento)' "Condicion de abrir inesperada: '$(if ($abrir) { $abrir[3] })'"
Exigir ($null -ne $cerrar -and $null -ne $abrir -and [int]$abrir[4] -lt [int]$cerrar[4]) 'Primero se abre SocialHub y despues se cierra el dialogo' 'Finalizar no cierra el dialogo despues de abrir SocialHub'

# --- Accion personalizada de abrir: inmediata, asincrona sin esperar, con explorer.exe (usuario normal)
$acciones = @(Consultar 'SELECT `Action`, `Type`, `Source`, `Target` FROM `CustomAction`')
$ca = $acciones | Where-Object { $_[0] -eq 'SH_AbrirSocialHub' } | Select-Object -First 1
Exigir ($null -ne $ca) 'Existe la accion SH_AbrirSocialHub' 'FALTA la accion SH_AbrirSocialHub'
if ($ca) {
  $tipo = [int]$ca[1]
  Escribir "  SH_AbrirSocialHub: tipo $tipo, directorio $($ca[2]), orden: $($ca[3])"
  Exigir (($tipo -band 0x3F) -eq 34) 'Es de tipo 34 (ejecutar un programa con linea de ordenes)' "Tipo base $($tipo -band 0x3F), no 34"
  Exigir (($tipo -band 0xC0) -eq 0xC0) 'Asincrona y sin esperar ni comprobar el resultado (no puede bloquear ni hacer fallar la instalacion)' "No es asyncNoWait (tipo $tipo)"
  Exigir (($tipo -band 0x400) -eq 0) 'Inmediata (no dentro de la instalacion elevada)' "Es diferida (tipo $tipo): se ejecutaria elevada"
  Exigir (($tipo -band 0x800) -eq 0) 'Con la identidad del usuario (no NoImpersonate)' "Es NoImpersonate (tipo $tipo)"
  Exigir ($ca[3] -match 'explorer\.exe' -and $ca[3] -match '\[INSTALLDIR\]SocialHub\.exe') 'Abre [INSTALLDIR]SocialHub.exe a traves de explorer.exe' "La orden no abre SocialHub.exe con explorer.exe: $($ca[3])"
}

# --- Secuencia de la interfaz: la pantalla final sale al terminar bien y la barra de progreso antes de ejecutar
$ui = @(Consultar 'SELECT `Action`, `Condition`, `Sequence` FROM `InstallUISequence`')
$salidaOk = $ui | Where-Object { $_[0] -eq 'ExitDialog' -and [int]$_[2] -eq -1 }
Exigir ($null -ne $salidaOk) 'InstallUISequence muestra ExitDialog al terminar bien (OnExit success)' 'InstallUISequence no muestra ExitDialog al terminar bien'
$prog = $ui | Where-Object { $_[0] -eq 'ProgressDlg' } | Select-Object -First 1
$exec = $ui | Where-Object { $_[0] -eq 'ExecuteAction' } | Select-Object -First 1
Exigir ($null -ne $prog -and $null -ne $exec -and [int]$prog[2] -lt [int]$exec[2]) 'La barra de progreso sale antes de ExecuteAction' 'ProgressDlg no esta antes de ExecuteAction'
$primeras = @($ui | Where-Object { [int]$_[2] -gt 0 } | Sort-Object { [int]$_[2] } | ForEach-Object { $_[0] })
Escribir "  InstallUISequence (orden): $($primeras -join ' > ')"
$conPantallaAntes = @($ui | Where-Object { [int]$_[2] -gt 0 -and [int]$_[2] -lt [int]$exec[2] -and $_[0] -match 'Dlg$|Dialog$' -and $_[0] -ne 'ProgressDlg' })
Exigir ($conPantallaAntes.Count -eq 0) 'Ninguna pantalla se muestra antes de la barra de progreso' "Pantallas antes de instalar: $(($conPantallaAntes | ForEach-Object { $_[0] }) -join ', ')"

# --- Textos de cada caso (lo que cambia la accion de propiedad, no se ve en la CI)
function TextoAccion([string]$id) { ($acciones | Where-Object { $_[0] -eq $id } | Select-Object -First 1)[3] }
foreach ($par in @(
    @('SH_TituloActualizado', 'actualizado correctamente'), @('SH_TituloMantenimiento', 'ya estaba instalado'),
    @('SH_TituloDesinstalado', 'Se ha desinstalado SocialHub'), @('SH_DescripcionDesinstalado', '%LOCALAPPDATA%\SocialHub'))) {
  $t = TextoAccion $par[0]
  Exigir ($t -like "*$($par[1])*") "$($par[0]) = «$t»" "$($par[0]) no contiene «$($par[1])» (es '$t')"
}
foreach ($id in 'SH_TituloActualizado', 'SH_TituloMantenimiento', 'SH_TituloDesinstalado', 'SH_DescripcionActualizado', 'SH_DescripcionMantenimiento', 'SH_DescripcionDesinstalado') {
  Exigir (@($ui | Where-Object { $_[0] -eq $id }).Count -eq 1) "$id esta en la secuencia de la interfaz" "$id no esta en InstallUISequence"
}
Exigir ((@($ui | Where-Object { $_[0] -eq 'SH_TituloDesinstalado' })[0][1]) -match 'REMOVE') 'El texto de desinstalar solo se pone al quitar (REMOVE)' 'La condicion del texto de desinstalar no mira REMOVE'

# --- Lo de antes sigue igual
$upg = @(Consultar 'SELECT `UpgradeCode`, `VersionMin`, `VersionMax`, `Attributes` FROM `Upgrade`')
Escribir "  Upgrade: $(($upg | ForEach-Object { $_ -join ' | ' }) -join ' ;; ')"
$codigoEsperado = '{' + $UpgradeCode.ToUpper() + '}'
Exigir (@($upg | Where-Object { $_[0] -eq $codigoEsperado }).Count -ge 1) "El upgrade-uuid sigue siendo $codigoEsperado" "El upgrade-uuid del .msi no es $codigoEsperado"
$dirs = @(Consultar 'SELECT `Directory`, `Directory_Parent`, `DefaultDir` FROM `Directory`' | Where-Object { $_[0] -eq 'INSTALLDIR' -or $_[2] -match 'SocialHub' })
Escribir "  Directory: $(($dirs | ForEach-Object { $_ -join ' | ' }) -join ' ;; ')"
$inst = $dirs | Where-Object { $_[0] -eq 'INSTALLDIR' } | Select-Object -First 1
Exigir ($null -ne $inst -and $inst[2] -match 'SocialHub' -and $inst[1] -match 'ProgramFiles') 'Se instala en Archivos de programa\SocialHub (INSTALLDIR bajo ProgramFiles)' 'INSTALLDIR ya no cuelga de Archivos de programa\SocialHub'
$accesos = @(Tabla 'Shortcut')
Escribir "  Accesos directos: $(($accesos | ForEach-Object { $_[0] + ' -> ' + $_[1] }) -join ', ')"
Exigir ($accesos.Count -ge 2) 'Hay acceso directo en el escritorio y en el menu Inicio' "Solo hay $($accesos.Count) accesos directos"
$condAcc = @(Consultar 'SELECT `Component`, `Condition` FROM `Component`' | Where-Object { $_[1] -match 'JP_INSTALL_(DESKTOP|STARTMENU)_SHORTCUT' })
Exigir ($condAcc.Count -ge 2) 'Los accesos directos siguen creandose por defecto (JP_INSTALL_*_SHORTCUT = 1)' 'Cambiaron las condiciones de los accesos directos'
Exigir (($props['JP_INSTALL_DESKTOP_SHORTCUT'] -eq '1') -and ($props['JP_INSTALL_STARTMENU_SHORTCUT'] -eq '1')) 'JP_INSTALL_DESKTOP_SHORTCUT y JP_INSTALL_STARTMENU_SHORTCUT valen 1' 'Los accesos directos ya no valen 1 por defecto'
$secEj = @(Consultar 'SELECT `Action` FROM `InstallExecuteSequence`' | ForEach-Object { $_[0] })
Exigir (($secEj -contains 'RemoveExistingProducts') -and ($secEj -contains 'JpFindRelatedProducts')) 'La actualizacion sobre una version anterior sigue (RemoveExistingProducts y JpFindRelatedProducts)' 'Falta RemoveExistingProducts o JpFindRelatedProducts en InstallExecuteSequence'
Exigir ($secEj -notcontains 'ScheduleReboot' -and $secEj -notcontains 'ForceReboot') 'No se fuerza ningun reinicio: se pide solo cuando Windows lo necesita' 'El .msi fuerza reinicios (ScheduleReboot o ForceReboot)'

if ($Informe) { $salida | Set-Content -Path $Informe -Encoding utf8 }
if ($fallos.Count -gt 0) {
  Write-Host ""
  Write-Host "FALLOS ($($fallos.Count)):"
  $fallos | ForEach-Object { Write-Host "  - $_" }
  exit 1
}
Write-Host "La interfaz del .msi es la esperada."
