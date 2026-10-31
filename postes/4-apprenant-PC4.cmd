@echo off
chcp 65001 >nul
title IHM 2026 - PC4 APPRENANT
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

REM =====================================================================
REM  PC4 : APPRENANT
REM
REM  Le role APPRENANT est fixe par ce script.
REM  Le serveur est decouvert automatiquement ; le PIN individuel est saisi a l'inscription.
REM =====================================================================

call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

set "PORT=7777"
echo =====================================================================
echo    POSTE APPRENANT — ROLE FIXE
echo    Ce script ne peut ouvrir que l'interface apprenant.
echo =====================================================================
set "ARGS=--role=apprenant --poste=PC4 --port=%PORT%"

echo =====================================================================
echo    IHM 2026 - PC4 : APPRENANT
echo =====================================================================
echo    Role verrouille : APPRENANT. Connexion au serveur obligatoire.
echo.
echo    Compilation puis demarrage. Au premier lancement, Maven se telecharge
echo    (~9 Mo) puis les dependances : cela peut prendre quelques minutes.
echo    Les lignes [INFO] qui defilent sont normales.
echo    Si rien ne s'affiche en 5 minutes, lancer postes\verifier.cmd
echo =====================================================================
echo.

call "%MVN%" -B javafx:run "-Djavafx.args=%ARGS%"
if errorlevel 1 (
    echo.
    echo [ERREUR] La fenetre du poste PC4 n'a pas pu s'ouvrir.
    pause
) else (
    echo.
    echo La fenetre du poste est fermee.
    pause
)
endlocal
