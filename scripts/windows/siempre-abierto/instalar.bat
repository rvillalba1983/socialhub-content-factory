@echo off
chcp 65001 >nul
title Siempre abierto - instalar
echo Siempre abierto: mantiene abiertos Claude, OpenCode y Chrome, y evita que Windows se duerma.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
echo.
pause
