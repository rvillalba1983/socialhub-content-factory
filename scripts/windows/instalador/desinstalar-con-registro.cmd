@echo off
rem Desinstala SocialHub y deja un registro detallado: msiexec /x ... /l*v
rem Uso normal: doble clic. Para pruebas automaticas: anadir /silencioso.
rem Los datos (%LOCALAPPDATA%\SocialHub) no se borran.
rem Tambien quita la entrada de "abrir al iniciar sesion" del usuario (HKCU\...\Run\SocialHub): no pide administrador.
setlocal EnableExtensions DisableDelayedExpansion
set "MODO=%~1"
set "QN="
if /i "%MODO%"=="/silencioso" set "QN=/qn"

set "LOGDIR=%LOCALAPPDATA%\SocialHub\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
if not exist "%LOGDIR%" set "LOGDIR=%TEMP%"

rem Arranque con Windows: la entrada es del usuario y un .msi no puede limpiarla; si no existe, no pasa nada
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v SocialHub /f >nul 2>&1

set "STAMP="
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"`) do set "STAMP=%%T"
if not defined STAMP set "STAMP=%RANDOM%%RANDOM%"
set "LOG=%LOGDIR%\desinstalacion-%STAMP%.log"

rem El codigo de producto de la version instalada (para no depender de que el .msi sea el mismo)
set "CODE="
for /f "usebackq delims=" %%G in (`powershell -NoProfile -Command "Get-ChildItem 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall','HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall' -ErrorAction SilentlyContinue | Get-ItemProperty | Where-Object { $_.DisplayName -eq 'SocialHub' } | Select-Object -First 1 -ExpandProperty PSChildName"`) do set "CODE=%%G"
if not defined CODE goto :noinstalado
echo %CODE%| findstr /r "^{.*}$" >nul
if errorlevel 1 goto :noinstalado

echo.
echo Desinstalando SocialHub. Registro de la desinstalacion:
echo   %LOG%
echo Cierra SocialHub y Chrome antes de seguir. Tus datos no se borran.
echo.
if not defined QN pause

start "" /wait msiexec /x "%CODE%" /l*v "%LOG%" %QN%
set "RC=%ERRORLEVEL%"

echo.
echo Codigo de salida: %RC%
echo Registro de la desinstalacion: %LOG%
if "%RC%"=="0" echo Resultado: desinstalado.
if "%RC%"=="3010" echo Resultado: desinstalado, pero Windows pide REINICIAR.
if "%RC%"=="1602" echo Resultado: desinstalacion cancelada.
if "%RC%"=="1603" echo Resultado: error grave. Guarda el registro de arriba.
if "%RC%"=="1618" echo Resultado: hay otra instalacion de Windows en marcha. Espera y repite.
goto :fin

:noinstalado
echo SocialHub no aparece como instalado en este ordenador.
set "RC=0"

:fin
if not defined QN pause
endlocal & exit /b %RC%
