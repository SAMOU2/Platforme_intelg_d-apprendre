@echo off
chcp 65001 >nul
REM =====================================================================
REM  MISE A JOUR + RLANCE EN UN SEUL DOUBLE-CLIC
REM
REM  Ce script :
REM    1. ferme les fenetres du projet en cours (session interrompue),
REM    2. recupere la derniere version depuis GitHub (branche main),
REM    3. compile,
REM    4. relance authentification, serveur de salle puis PC4,
REM       en attendant que le port HTTPS soit annonce.
REM
REM  Il ne supprime aucune donnee : ni la base, ni les comptes, ni les
REM  certificats. Il ne touche a aucun autre logiciel que le projet.
REM =====================================================================
setlocal
cd /d "%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0maj-et-relancer-PC1.ps1"
set "RESULTAT=%errorlevel%"
echo.
if not "%RESULTAT%"=="0" echo Le script s'est arrete avec le code %RESULTAT%. Lire les messages ci-dessus.
pause
endlocal
exit /b %RESULTAT%
