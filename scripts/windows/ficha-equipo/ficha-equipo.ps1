# Ficha del equipo · Apunta en un .txt lo necesario para decidir si un PC sirve como servidor de SocialHub
# Solo lee: no instala ni cambia nada. Deja ficha-<NOMBRE DEL EQUIPO>.txt junto a este script.
# Algunos datos (TPM y arranque seguro) solo se ven ejecutándolo como administrador.

$ErrorActionPreference = 'SilentlyContinue'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $here ("ficha-{0}.txt" -f $env:COMPUTERNAME)
$lines = New-Object System.Collections.Generic.List[string]
function Add([string]$t) { $lines.Add($t); Write-Host $t }
function GB($bytes) { if ($bytes) { '{0:N1} GB' -f ($bytes / 1GB) } else { '?' } }

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

Add ("Ficha del equipo · {0} · {1}" -f $env:COMPUTERNAME, (Get-Date -Format 'yyyy-MM-dd HH:mm'))
Add ("Ejecutada como administrador: {0}" -f $(if ($admin) { 'sí' } else { 'no (TPM y arranque seguro pueden salir como «?»)' }))
Add ''

# Equipo
$cs   = Get-CimInstance Win32_ComputerSystem
$bios = Get-CimInstance Win32_BIOS
$enc  = Get-CimInstance Win32_SystemEnclosure
$bat  = Get-CimInstance Win32_Battery
Add '== Equipo'
Add ("Fabricante y modelo: {0} {1}" -f $cs.Manufacturer, $cs.Model)
Add ("Número de serie: {0}" -f $bios.SerialNumber)
Add ("BIOS: {0} ({1})" -f $bios.SMBIOSBIOSVersion, $(if ($bios.ReleaseDate) { $bios.ReleaseDate.ToString('yyyy-MM-dd') } else { '?' }))
Add ("Tipo: {0}" -f $(if ($bat) { 'portátil (tiene batería)' } else { 'sobremesa (sin batería)' }))
if ($bat) { Add ("Batería: {0} % de carga ahora" -f $bat.EstimatedChargeRemaining) }
Add ''

# Procesador
Add '== Procesador'
foreach ($c in Get-CimInstance Win32_Processor) {
  Add ("{0} · {1} núcleos / {2} hilos · {3} MHz" -f $c.Name.Trim(), $c.NumberOfCores, $c.NumberOfLogicalProcessors, $c.MaxClockSpeed)
  # Generación de Intel Core por el número de modelo (Windows 11 pide 8.ª o posterior)
  if ($c.Name -match 'i[3579]-(\d{4,5})') {
    $m = $Matches[1]; $gen = if ($m.Length -eq 5) { [int]$m.Substring(0, 2) } else { [int]$m.Substring(0, 1) }
    Add ("Intel Core de {0}.ª generación · Windows 11: {1}" -f $gen, $(if ($gen -ge 8) { 'el procesador sí cumple' } else { 'el procesador NO cumple (hace falta 8.ª o posterior)' }))
  } elseif ($c.Name -match 'Ryzen') {
    Add 'AMD Ryzen: Windows 11 pide serie 2000 o posterior (mirar el número de modelo)'
  }
}
Add ''

# Memoria
$mods  = @(Get-CimInstance Win32_PhysicalMemory)
$array = Get-CimInstance Win32_PhysicalMemoryArray | Select-Object -First 1
Add '== Memoria'
Add ("Total: {0}" -f (GB $cs.TotalPhysicalMemory))
Add ("Módulos: {0} de {1} ranuras · máximo admitido: {2}" -f $mods.Count, $array.MemoryDevices, $(if ($array.MaxCapacityEx) { GB ($array.MaxCapacityEx * 1KB) } elseif ($array.MaxCapacity) { GB ($array.MaxCapacity * 1KB) } else { '?' }))
foreach ($m in $mods) { Add ("  - {0} · {1} MHz · {2}" -f (GB $m.Capacity), $m.Speed, $m.DeviceLocator) }
Add ''

# Discos
Add '== Discos'
$pd = @(Get-PhysicalDisk)
if ($pd) {
  foreach ($d in $pd) { Add ("{0} · {1} · {2} · {3}" -f $d.FriendlyName, $d.MediaType, (GB $d.Size), $d.BusType) }
} else {
  foreach ($d in Get-CimInstance Win32_DiskDrive) { Add ("{0} · {1} · {2}" -f $d.Model, (GB $d.Size), $d.InterfaceType) }
}
foreach ($v in Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3') {
  Add ("Unidad {0} {1} · libre {2} de {3}" -f $v.DeviceID, $v.VolumeName, (GB $v.FreeSpace), (GB $v.Size))
}
Add ''

# Windows
$os = Get-CimInstance Win32_OperatingSystem
$cv = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
Add '== Windows'
Add ("{0} · versión {1} · compilación {2}.{3}" -f $os.Caption, $cv.DisplayVersion, $os.BuildNumber, $cv.UBR)
Add ("Instalado: {0} · encendido desde: {1}" -f $os.InstallDate.ToString('yyyy-MM-dd'), $os.LastBootUpTime.ToString('yyyy-MM-dd HH:mm'))
$fw = if (Test-Path 'HKLM:\SYSTEM\CurrentControlSet\Control\SecureBoot\State') { 'UEFI' } else { 'BIOS heredada (Windows 11 pide UEFI)' }
Add ("Arranque: {0}" -f $fw)
$sb = $null; try { $sb = Confirm-SecureBootUEFI } catch { }
Add ("Arranque seguro: {0}" -f $(if ($sb -eq $true) { 'activado' } elseif ($sb -eq $false) { 'desactivado' } else { '?' }))
$tpm = $null; try { $tpm = Get-CimInstance -Namespace 'root\cimv2\security\microsofttpm' -ClassName Win32_Tpm } catch { }
Add ("TPM: {0}" -f $(if ($tpm) { "versión $($tpm.SpecVersion.Split(',')[0]) · activo: $($tpm.IsEnabled_InitialValue)" } else { '? (o no tiene)' }))
Add ''

# Red
Add '== Red'
foreach ($n in Get-CimInstance Win32_NetworkAdapter -Filter 'NetEnabled=True') { Add ("{0} · {1} Mbps" -f $n.Name, [math]::Round($n.Speed / 1e6)) }
Add ''

# Programas de SocialHub
Add '== Programas'
$startApps = @(); try { $startApps = @(Get-StartApps) } catch { }
foreach ($name in 'Claude', 'OpenCode', 'Google Chrome', 'Google Drive', 'SocialHub') {
  $hit = $startApps | Where-Object { $_.Name -like "$name*" } | Select-Object -First 1
  Add ("{0}: {1}" -f $name, $(if ($hit) { 'instalado' } else { 'no encontrado' }))
}
$sh = Join-Path $env:LOCALAPPDATA 'SocialHub'
if (Test-Path $sh) {
  $size = (Get-ChildItem $sh -Recurse -File | Measure-Object Length -Sum).Sum
  Add ("Datos de SocialHub: {0} ({1})" -f $sh, (GB $size))
}
Add ''

# Consumo y suspensión
Add '== Energía'
$plan = (powercfg /getactivescheme) -join ' '
Add ("Plan: {0}" -f ($plan -replace '^.*\((.*)\).*$', '$1'))
Add ("Suspensión: {0}" -f ((powercfg /a | Select-String 'Standby|Suspensión|Hibernate|Hibernación' | Select-Object -First 3 | ForEach-Object { $_.Line.Trim() }) -join ' · '))

$lines | Set-Content -Path $out -Encoding UTF8
Write-Host ''
Write-Host "Guardado en $out"
