@echo off
rem Instala SocialHub y deja siempre un registro detallado: msiexec /i ... /l*v
rem Uso normal: doble clic, con este archivo junto al SocialHub-<version>.msi.
rem   Al terminar abre una ventana de aviso con el resultado, en espanol.
rem Para pruebas automaticas: anadir /silencioso (sin ventanas, sin pausa y sin aviso).
rem   /aviso-prueba: como /silencioso pero SI prepara el aviso; escribe su texto en el archivo de la variable SOCIALHUB_AVISO_ARCHIVO en vez de abrir la ventana.
rem No pide ni guarda contrasenas ni claves. Solo escribe en %LOCALAPPDATA%\SocialHub\logs.
rem Solo ASCII: los acentos del aviso se escriben con marcas (#a #e #i #o #u #n #! #?) que PowerShell convierte al abrir la ventana.
setlocal EnableExtensions DisableDelayedExpansion
set "MODO=%~1"
set "QN="
set "AVISO=1"
set "PRUEBA="
if /i "%MODO%"=="/silencioso" set "QN=/qn" & set "AVISO="
if /i "%MODO%"=="/aviso-prueba" set "QN=/qn" & set "PRUEBA=1"

set "MSI="
for %%F in ("%~dp0SocialHub-*.msi") do set "MSI=%%~fF"
if not defined MSI goto :sinmsi

set "LOGDIR=%LOCALAPPDATA%\SocialHub\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
if not exist "%LOGDIR%" set "LOGDIR=%TEMP%"

set "STAMP="
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%T"
if not defined STAMP set "STAMP=%RANDOM%%RANDOM%"
set "LOG=%LOGDIR%\instalacion-%STAMP%.log"

echo.
echo Instalando SocialHub desde:
echo   %MSI%
echo Registro de la instalacion:
echo   %LOG%
echo.
echo Antes de seguir, cierra SocialHub (boton Cerrar SocialHub de la web) y Chrome:
echo si estan abiertos, Windows puede dejar la instalacion a medias y pedir reiniciar.
echo Windows te pedira permiso de administrador.
echo.
if not defined QN pause

start "" /wait msiexec /i "%MSI%" /l*v "%LOG%" %QN%
set "RC=%ERRORLEVEL%"

echo.
echo Codigo de salida: %RC%
echo Registro de la instalacion: %LOG%
set "ICONO=Error"
set "TEXTO=La instalaci#on ha terminado con el c#odigo %RC%. Guarda el registro y mand#alo: dice paso a paso qu#e hizo Windows."
if "%RC%"=="0" echo Resultado: instalacion correcta.
if "%RC%"=="0" set "ICONO=Information" & set "TEXTO=SocialHub se ha instalado correctamente."
if "%RC%"=="3010" echo Resultado: instalada, pero Windows pide REINICIAR. Cierra SocialHub y Chrome y reinicia el ordenador antes de abrir SocialHub.
if "%RC%"=="3010" set "ICONO=Warning" & set "TEXTO=SocialHub se ha instalado, pero Windows necesita REINICIAR el ordenador. Cierra SocialHub y Chrome, reinicia y abre SocialHub despu#es."
if "%RC%"=="1641" echo Resultado: instalada; Windows va a reiniciar el ordenador.
if "%RC%"=="1641" set "ICONO=Warning" & set "TEXTO=SocialHub se ha instalado. Windows va a reiniciar el ordenador."
if "%RC%"=="1602" echo Resultado: instalacion cancelada.
if "%RC%"=="1602" set "ICONO=Warning" & set "TEXTO=La instalaci#on se ha cancelado. No se ha instalado SocialHub."
if "%RC%"=="1603" echo Resultado: error grave durante la instalacion. Guarda el registro de arriba.
if "%RC%"=="1603" set "TEXTO=La instalaci#on ha fallado: error grave, c#odigo 1603. Guarda el registro y mand#alo: dice paso a paso qu#e hizo Windows."
if "%RC%"=="1618" echo Resultado: hay otra instalacion de Windows en marcha. Espera a que termine y repite.
if "%RC%"=="1618" set "ICONO=Warning" & set "TEXTO=Hay otra instalaci#on de Windows en marcha. Espera a que termine y vuelve a abrir este instalador."
if "%RC%"=="1619" echo Resultado: Windows no pudo abrir el instalador. Descargalo de nuevo.
if "%RC%"=="1619" set "TEXTO=Windows no pudo abrir el instalador. Desc#argalo de nuevo."
if "%RC%"=="1620" echo Resultado: el instalador esta danado. Descargalo de nuevo.
if "%RC%"=="1620" set "TEXTO=El instalador est#a da#nado. Desc#argalo de nuevo."
if "%RC%"=="1925" echo Resultado: faltan permisos de administrador.
if "%RC%"=="1925" set "TEXTO=Faltan permisos de administrador para instalar SocialHub."
echo.
echo Si algo falla, guarda el registro y mandalo: dice paso a paso que hizo Windows.
set "REGISTRO=%LOG%"
goto :fin

:sinmsi
echo No encuentro el instalador SocialHub-*.msi en esta carpeta:
echo   %~dp0
echo Deja este archivo junto al .msi y vuelve a abrirlo.
set "RC=2"
set "REGISTRO="
set "ICONO=Error"
set "TEXTO=No encuentro el instalador SocialHub-*.msi en esta carpeta. Deja este archivo junto al .msi y vuelve a abrirlo."

:fin
if defined AVISO call :aviso
if not defined QN pause
endlocal & exit /b %RC%

:aviso
rem Ventana de aviso: PowerShell + Windows Forms, ya incluidos en Windows (sin librerias). Dentro del -Command solo comillas simples.
set "SH_ICONO=%ICONO%"
set "SH_TEXTO=%TEXTO%"
set "SH_REGISTRO=%REGISTRO%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=$env:SH_TEXTO; foreach($p in @(@('#a',225),@('#e',233),@('#i',237),@('#o',243),@('#u',250),@('#n',241),@('#!',161),@('#?',191))){ $t=$t.Replace($p[0],[string][char]$p[1]) }; if($env:SH_REGISTRO){ $t=$t+[char]13+[char]10+[char]13+[char]10+'Registro: '+$env:SH_REGISTRO }; if($env:SOCIALHUB_AVISO_ARCHIVO){ Set-Content -LiteralPath $env:SOCIALHUB_AVISO_ARCHIVO -Value ($env:SH_ICONO+'|'+$t) -Encoding UTF8; exit 0 }; Add-Type -AssemblyName System.Windows.Forms; $f=New-Object System.Windows.Forms.Form; $f.TopMost=$true; [void][System.Windows.Forms.MessageBox]::Show($f,$t,'SocialHub',[System.Windows.Forms.MessageBoxButtons]::OK,[System.Windows.Forms.MessageBoxIcon]$env:SH_ICONO)" >nul 2>&1
rem Si no se pudo abrir la ventana, se deja la pausa de siempre para poder leer el resultado en la consola
if errorlevel 1 if not defined PRUEBA set "QN="
exit /b 0
