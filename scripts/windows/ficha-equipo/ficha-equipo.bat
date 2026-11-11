@echo off
chcp 65001 >nul
title Ficha del equipo
echo Ficha del equipo: apunta procesador, memoria, discos, Windows y programas. Solo lee, no cambia nada.
echo Para ver tambien TPM y arranque seguro: clic derecho, "Ejecutar como administrador".
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ficha-equipo.ps1"
echo.
pause
