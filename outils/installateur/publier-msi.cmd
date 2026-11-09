@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0publier-msi.ps1"
if errorlevel 1 (
  echo.
  echo La publication du brouillon a echoue. Lire le message ci-dessus.
  pause
  exit /b 1
)
echo.
pause
