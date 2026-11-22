@echo off
rem Desinstala SocialHub y deja un registro detallado: msiexec /x ... /l*v
rem Uso normal: doble clic. Al terminar abre una ventana de aviso con el resultado, en espanol.
rem Para pruebas automaticas: anadir /silencioso (sin ventanas, sin pausa y sin aviso).
rem   /aviso-prueba: como /silencioso pero SI prepara el aviso; escribe su texto en el archivo de la variable SOCIALHUB_AVISO_ARCHIVO en vez de abrir la ventana.
rem Los datos (%LOCALAPPDATA%\SocialHub) no se borran.
rem Solo ASCII: los acentos del aviso se escriben con marcas (#a #e #i #o #u #n #! #?) que PowerShell convierte al abrir la ventana.
setlocal EnableExtensions DisableDelayedExpansion
set "MODO=%~1"
set "QN="
set "AVISO=1"
set "PRUEBA="
if /i "%MODO%"=="/silencioso" set "QN=/qn" & set "AVISO="
if /i "%MODO%"=="/aviso-prueba" set "QN=/qn" & set "PRUEBA=1"

set "LOGDIR=%LOCALAPPDATA%\SocialHub\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
if not exist "%LOGDIR%" set "LOGDIR=%TEMP%"

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
set "ICONO=Error"
set "TEXTO=La desinstalaci#on ha terminado con el c#odigo %RC%. Guarda el registro y mand#alo: dice paso a paso qu#e hizo Windows."
if "%RC%"=="0" echo Resultado: desinstalado.
if "%RC%"=="0" set "ICONO=Information" & set "TEXTO=Se ha desinstalado SocialHub. Tus datos siguen en %LOCALAPPDATA%\SocialHub."
if "%RC%"=="3010" echo Resultado: desinstalado, pero Windows pide REINICIAR.
if "%RC%"=="3010" set "ICONO=Warning" & set "TEXTO=Se ha desinstalado SocialHub, pero Windows necesita REINICIAR el ordenador. Tus datos siguen en %LOCALAPPDATA%\SocialHub."
if "%RC%"=="1641" echo Resultado: desinstalado; Windows va a reiniciar el ordenador.
if "%RC%"=="1641" set "ICONO=Warning" & set "TEXTO=Se ha desinstalado SocialHub. Windows va a reiniciar el ordenador. Tus datos siguen en %LOCALAPPDATA%\SocialHub."
if "%RC%"=="1602" echo Resultado: desinstalacion cancelada.
if "%RC%"=="1602" set "ICONO=Warning" & set "TEXTO=La desinstalaci#on se ha cancelado. SocialHub sigue instalado."
if "%RC%"=="1603" echo Resultado: error grave. Guarda el registro de arriba.
if "%RC%"=="1603" set "TEXTO=La desinstalaci#on ha fallado: error grave, c#odigo 1603. Guarda el registro y mand#alo: dice paso a paso qu#e hizo Windows."
if "%RC%"=="1618" echo Resultado: hay otra instalacion de Windows en marcha. Espera y repite.
if "%RC%"=="1618" set "ICONO=Warning" & set "TEXTO=Hay otra instalaci#on de Windows en marcha. Espera a que termine y vuelve a abrir este desinstalador."
set "REGISTRO=%LOG%"
goto :fin

:noinstalado
echo SocialHub no aparece como instalado en este ordenador.
set "RC=0"
set "REGISTRO="
set "ICONO=Information"
set "TEXTO=SocialHub no aparece como instalado en este ordenador. No hay nada que desinstalar."

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
