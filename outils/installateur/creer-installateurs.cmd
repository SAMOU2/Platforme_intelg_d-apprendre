@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0creer-installateurs.ps1"
if errorlevel 1 (
  echo.
  echo La fabrication a echoue. Lire le message ci-dessus.
  pause
  exit /b 1
)
echo.
pause
