@echo off
chcp 65001 >nul
title SocialHub - prueba de PostgreSQL
echo SocialHub - prueba de PostgreSQL incluido
echo No instala nada: todo queda dentro de la carpeta prueba-postgres.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0probar-postgres.ps1"
echo.
echo Terminado. El resultado esta en informe-postgres.txt
pause
