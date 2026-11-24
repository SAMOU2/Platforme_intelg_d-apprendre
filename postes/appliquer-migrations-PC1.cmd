@echo off
chcp 65001 >nul
title IHM 2026 - migrations de la base PC1
setlocal
cd /d "%~dp0.."

REM =====================================================================
REM  Applique les migrations db\*.sql absentes d'un volume PostgreSQL
REM  deja cree. C'est la reponse au message :
REM    « Mise a jour de la base necessaire : appliquer les migrations
REM      10, 11, 12 et 16 sur PC1. »
REM
REM  Ce script NE SUPPRIME RIEN : ni table, ni donnee, ni volume.
REM  Il lit l'etat de la base, affiche les migrations manquantes, fait
REM  une sauvegarde dans sauvegardes\, puis applique uniquement celles
REM  qui manquent (chaque fichier est idempotent).
REM
REM  Usage :
REM    postes\appliquer-migrations-PC1.cmd                  (normal)
REM    postes\appliquer-migrations-PC1.cmd -Simulation      (n'ecrit rien)
REM    postes\appliquer-migrations-PC1.cmd -AvecDemonstration
REM         ajoute 17-offres-demonstration.sql (offres de demo)
REM =====================================================================

if exist "%~dp0configuration-PC1.local.cmd" (
    call "%~dp0configuration-PC1.local.cmd"
) else (
    echo [ATTENTION] postes\configuration-PC1.local.cmd absent : seul l'environnement Windows sera utilise.
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0appliquer-migrations-PC1.ps1" %*
set "RESULTAT=%ERRORLEVEL%"
echo.
if not "%RESULTAT%"=="0" (
    echo [ECHEC] Les migrations n'ont pas toutes ete appliquees. Lire le message ci-dessus.
    echo         Aucune donnee n'a ete supprimee. Une sauvegarde a pu etre creee dans sauvegardes\.
) else (
    echo Termine. Redemarrer ensuite postes\authentification-PC1.bat.
)
if not defined IHM_NO_PAUSE pause
endlocal
exit /b %RESULTAT%
