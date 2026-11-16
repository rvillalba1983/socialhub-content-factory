@echo off
rem Instala SocialHub y deja siempre un registro detallado: msiexec /i ... /l*v
rem Uso normal: doble clic, con este archivo junto al SocialHub-<version>.msi.
rem Para pruebas automaticas: anadir /silencioso (sin ventanas ni pausa final).
rem No pide ni guarda contrasenas ni claves. Solo escribe en %LOCALAPPDATA%\SocialHub\logs.
setlocal EnableExtensions DisableDelayedExpansion
set "MODO=%~1"
set "QN="
if /i "%MODO%"=="/silencioso" set "QN=/qn"

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
if "%RC%"=="0" echo Resultado: instalacion correcta.
if "%RC%"=="3010" echo Resultado: instalada, pero Windows pide REINICIAR. Cierra SocialHub y Chrome y reinicia el ordenador antes de abrir SocialHub.
if "%RC%"=="1641" echo Resultado: instalada; Windows va a reiniciar el ordenador.
if "%RC%"=="1602" echo Resultado: instalacion cancelada.
if "%RC%"=="1603" echo Resultado: error grave durante la instalacion. Guarda el registro de arriba.
if "%RC%"=="1618" echo Resultado: hay otra instalacion de Windows en marcha. Espera a que termine y repite.
if "%RC%"=="1619" echo Resultado: Windows no pudo abrir el instalador. Descargalo de nuevo.
if "%RC%"=="1620" echo Resultado: el instalador esta danado. Descargalo de nuevo.
if "%RC%"=="1925" echo Resultado: faltan permisos de administrador.
echo.
echo Si algo falla, guarda el registro y mandalo: dice paso a paso que hizo Windows.
goto :fin

:sinmsi
echo No encuentro el instalador SocialHub-*.msi en esta carpeta:
echo   %~dp0
echo Deja este archivo junto al .msi y vuelve a abrirlo.
set "RC=2"

:fin
if not defined QN pause
endlocal & exit /b %RC%
