@echo off
chcp 65001 >nul
title Siempre abierto - desinstalar
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0desinstalar.ps1"
echo.
pause
