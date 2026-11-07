@echo off
chcp 65001 >nul
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0autoriser-https-pare-feu.ps1"
echo.
pause
endlocal
