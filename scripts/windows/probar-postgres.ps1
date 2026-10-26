# SocialHub · Prueba de PostgreSQL incluido en Windows
# Descarga PostgreSQL 18.6 portable (binarios oficiales de EDB), crea una base de datos
# en esta carpeta, la arranca, escribe datos, la para, la vuelve a arrancar y comprueba
# que los datos siguen ahí. No instala nada en el sistema ni toca nada fuera de esta carpeta.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$base    = Split-Path -Parent $MyInvocation.MyCommand.Path
$work    = Join-Path $base 'prueba-postgres'
$zip     = Join-Path $work 'postgresql-18.6-windows-x64-binaries.zip'
$bin     = Join-Path $work 'pgsql\bin'
$data    = Join-Path $work 'datos'
$log     = Join-Path $work 'postgres.log'
$report  = Join-Path $base 'informe-postgres.txt'
$port    = 55432
$url     = 'https://sbp.enterprisedb.com/getfile.jsp?fileid=1260609'
$lines   = New-Object System.Collections.Generic.List[string]
$failed  = $false
$running = $false

function Note([string]$t) {
  $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $t
  Write-Host $line
  $lines.Add($line)
  # El informe se guarda en cada paso, para que quede aunque la ventana se cierre
  try { $lines | Set-Content -Path $report -Encoding UTF8 } catch { }
}

# Evita que Windows muestre ventanas de error (por ejemplo, una DLL que falta) y bloquee la prueba
try {
  Add-Type -Namespace SocialHub -Name Win -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetErrorMode(uint mode);'
  [SocialHub.Win]::SetErrorMode(0x8003) | Out-Null
} catch { }

function Step([string]$name, [scriptblock]$action) {
  if ($script:failed) { Note "SALTADO $name"; return }
  Write-Host ('           en curso: {0}...' -f $name) -ForegroundColor DarkGray
  $sw = [Diagnostics.Stopwatch]::StartNew()
  try {
    $extra = & $action
    $msg = 'OK      {0} ({1:N1} s)' -f $name, $sw.Elapsed.TotalSeconds
    if ($extra) { $msg += ' -> ' + ($extra | Out-String).Trim() }
    Note $msg
  } catch {
    $script:failed = $true
    Note ('ERROR   {0}: {1}' -f $name, $_.Exception.Message)
  }
}

# Programas que terminan solos (initdb, psql): se captura su salida.
function Run([string]$exe, [string[]]$argList) {
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $out = & $exe @argList 2>&1 | Out-String
  $code = $LASTEXITCODE
  $ErrorActionPreference = $old
  if ($code -ne 0) { throw "código de salida $code. $($out.Trim())" }
  return $out.Trim()
}

# pg_ctl: sin capturar la salida, para que el servidor no bloquee la espera.
function PgCtl([string]$arguments) {
  $psi = New-Object Diagnostics.ProcessStartInfo (Join-Path $bin 'pg_ctl.exe'), $arguments
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $p = [Diagnostics.Process]::Start($psi)
  $p.WaitForExit()
  if ($p.ExitCode -ne 0) { throw "pg_ctl terminó con código $($p.ExitCode). Revisa postgres.log" }
}

function Sql([string]$db, [string]$query) {
  Run (Join-Path $bin 'psql.exe') @('-h','127.0.0.1','-p',"$port",'-U','socialhub','-d',$db,'-v','ON_ERROR_STOP=1','-tA','-c',$query)
}

$startArgs = "-D `"$data`" -l `"$log`" -o `"-p $port -c listen_addresses=127.0.0.1`" -w -t 90 start"
$stopArgs  = "-D `"$data`" -m fast -w -t 60 stop"

Note 'SocialHub · prueba de PostgreSQL incluido'
Note ('Windows: {0} · PowerShell {1}' -f [Environment]::OSVersion.VersionString, $PSVersionTable.PSVersion)
try {
  $drive = (Get-Item $base).PSDrive
  Note ('Espacio libre en {0}: {1:N1} GB' -f $drive.Name, ($drive.Free / 1GB))
} catch { }

New-Item -ItemType Directory -Force -Path $work | Out-Null

Step 'Componentes de Microsoft Visual C++ (vcruntime140.dll)' {
  $sys = Join-Path $env:windir 'System32'
  $missing = @('vcruntime140.dll','vcruntime140_1.dll','msvcp140.dll') | Where-Object { -not (Test-Path (Join-Path $sys $_)) }
  if ($missing) { throw ('faltan ' + ($missing -join ', ') + '. Hay que instalar el paquete gratuito de Microsoft Visual C++ (x64)') }
  'instalados'
}

Step 'Puerto 55432 libre' {
  $busy = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if ($busy) { throw "el puerto $port ya está en uso" }
}

Step 'Descargar PostgreSQL 18.6 portable (unos 370 MB)' {
  if (Test-Path $zip) { return 'ya descargado' }
  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
  $tmp = "$zip.part"
  Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing
  $fs = [IO.File]::OpenRead($tmp); $b0 = $fs.ReadByte(); $b1 = $fs.ReadByte(); $fs.Close()
  if ($b0 -ne 0x50 -or $b1 -ne 0x4B) { throw 'lo descargado no es un archivo zip' }
  Rename-Item $tmp $zip
  '{0:N0} MB' -f ((Get-Item $zip).Length / 1MB)
}

Step 'Descomprimir (puede tardar unos minutos)' {
  $done = Join-Path $work 'descompresion-completa.txt'
  if (Test-Path $done) { return 'ya descomprimido' }
  # Se descomprime de nuevo aunque haya restos de un intento anterior que pudo quedar a medias.
  # tar.exe (incluido en Windows 10 y 11) es mucho más rápido que Expand-Archive; se omiten pgAdmin y StackBuilder, que no hacen falta.
  $tar = Join-Path $env:windir 'System32\tar.exe'
  if (Test-Path $tar) {
    Run $tar @('-xf', $zip, '-C', $work, '--exclude', 'pgsql/pgAdmin 4', '--exclude', 'pgsql/StackBuilder') | Out-Null
  } else {
    Expand-Archive -Path $zip -DestinationPath $work -Force
  }
  foreach ($f in @('bin\postgres.exe','bin\initdb.exe','bin\pg_ctl.exe','bin\psql.exe','share\postgres.bki','lib')) {
    if (-not (Test-Path (Join-Path $work "pgsql\$f"))) { throw "falta pgsql\$f tras descomprimir" }
  }
  Set-Content -Path $done -Value (Get-Date -Format s)
}

Step 'Versión de los binarios' { Run (Join-Path $bin 'postgres.exe') @('--version') }

Step 'Crear la carpeta de datos (initdb)' {
  if (Test-Path (Join-Path $data 'PG_VERSION')) { return 'ya existía; se reutiliza' }
  Run (Join-Path $bin 'initdb.exe') @('-D',$data,'-U','socialhub','-A','trust','-E','UTF8','--no-locale') | Out-Null
}

Step 'Arrancar el servidor' { PgCtl $startArgs; $script:running = $true }
Step 'Consultar la versión' { Sql 'postgres' 'select version();' }
Step 'Crear la base de datos socialhub_prueba' {
  $exists = Sql 'postgres' "select 1 from pg_database where datname='socialhub_prueba';"
  if ($exists -eq '1') { return 'ya existía' }
  Sql 'postgres' 'create database socialhub_prueba;' | Out-Null
}
Step 'Escribir un dato de prueba' {
  Sql 'socialhub_prueba' "create table if not exists prueba(id serial primary key, nota text, creada timestamptz default now()); insert into prueba(nota) values ('SocialHub en Windows');" | Out-Null
}
Step 'Parar el servidor' { PgCtl $stopArgs; $script:running = $false }
Step 'Arrancar otra vez' { PgCtl $startArgs; $script:running = $true }
Step 'Comprobar que los datos siguen ahí' {
  $n = Sql 'socialhub_prueba' 'select count(*) from prueba;'
  if ([int]$n -lt 1) { throw 'la tabla está vacía tras reiniciar' }
  "$n filas"
}
Step 'Parar el servidor (final)' { PgCtl $stopArgs; $script:running = $false }

if ($running) {
  try { PgCtl $stopArgs; Note 'Servidor parado tras el error' } catch { Note 'AVISO: no se pudo parar el servidor; ciérralo reiniciando el PC si hace falta' }
}

if ($failed) {
  Note 'RESULTADO: FALLO'
  if (Test-Path $log) {
    $lines.Add('')
    $lines.Add('--- Últimas líneas de postgres.log ---')
    Get-Content $log -Tail 40 | ForEach-Object { $lines.Add($_) }
  }
} else {
  Note 'RESULTADO: OK. PostgreSQL incluido funciona en este Windows.'
}

$lines | Set-Content -Path $report -Encoding UTF8
