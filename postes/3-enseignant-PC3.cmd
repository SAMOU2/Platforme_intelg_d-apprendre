@echo off
chcp 65001 >nul
title IHM 2026 - PC3 ENSEIGNANT
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

REM =====================================================================
REM  PC3 : ENSEIGNANT
REM
REM  Le role ENSEIGNANT est fixe par ce script.
REM  Saisir l'IP du serveur dans l'interface, ou utiliser IHM_SERVEUR.
REM =====================================================================

call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

set "PORT=7777"
REM Facultatif : IHM_SERVEUR contient l'IP du PC1, jamais localhost sur un client.
echo =====================================================================
echo    POSTE ENSEIGNANT — ROLE FIXE
echo    Ce script ne peut ouvrir que l'interface enseignant.
echo =====================================================================
set "ARGS=--role=enseignant --poste=PC3 --port=%PORT%"
if defined IHM_SERVEUR set "ARGS=!ARGS! --serveur=!IHM_SERVEUR!"

echo =====================================================================
echo    IHM 2026 - PC3 : ENSEIGNANT
echo =====================================================================
echo    Docker et PostgreSQL : sur PC1 uniquement, jamais sur ce client.
echo    Role verrouille : ENSEIGNANT. Connexion au serveur obligatoire.
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
    echo [ERREUR] La fenetre du poste PC3 n'a pas pu s'ouvrir.
    pause
    exit /b 1
) else (
    echo.
    echo La fenetre du poste est fermee.
    pause
)
endlocal
exit /b 0
