@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion
cd /d "%~dp0.."
call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0approuver-autorite-client.ps1"
set "RESULTAT=%errorlevel%"
pause
exit /b %RESULTAT%
