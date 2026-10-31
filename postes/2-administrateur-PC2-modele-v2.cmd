@echo off
chcp 65001 >nul
title IHM 2026 - PC2 ADMINISTRATEUR - MODELE V2 (TEST)
setlocal
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Dihm.modele=local-v2"
echo.
echo [AVERTISSEMENT] Le modele v2 est experimental. Les tests montrent des erreurs
echo de classement et des refus injustifies ; le modele v1 reste le choix normal.
echo.
call "%~dp02-administrateur-PC2.cmd"
exit /b %ERRORLEVEL%
