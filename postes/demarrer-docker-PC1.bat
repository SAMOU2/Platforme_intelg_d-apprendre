@echo off
chcp 65001 >nul
setlocal DisableDelayedExpansion
cd /d "%~dp0.."
REM Secrets charges uniquement dans l'environnement, jamais affiches.
if exist "%~dp0configuration-PC1.local.cmd" call "%~dp0configuration-PC1.local.cmd"
if /i "%~1"=="/auth" goto :auth
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0demarrer-docker-PC1.ps1"
goto :resultat
:auth
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0demarrer-docker-PC1.ps1" -PourAuthentification
:resultat
set "RESULTAT=%errorlevel%"
if /i "%~1"=="/auth" exit /b %RESULTAT%
if not "%RESULTAT%"=="0" pause
exit /b %RESULTAT%
