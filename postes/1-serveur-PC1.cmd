@echo off
chcp 65001 >nul
title IHM 2026 - PC1 serveur de la salle
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

REM =====================================================================
REM  PC1 : le SERVEUR de la salle.
REM
REM  Ce poste n'ouvre pas de fenetre graphique : les clients le decouvrent
REM  automatiquement sur le reseau local.
REM    PC2 administrateur - PC3 enseignant - PC4 apprenant
REM
REM  A lancer EN PREMIER. Laisser cette fenetre ouverte : la fermer
REM  eteint le serveur, et les autres postes le signalent.
REM =====================================================================

call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

set "PORT=7777"

echo =====================================================================
echo    IHM 2026 - PC1 : SERVEUR DE LA SALLE
echo =====================================================================
echo.
echo    Les postes clients decouvrent automatiquement ce serveur sur le LAN.
echo    Postes de la salle :
echo              PC2   administrateur
echo              PC3   enseignant
echo              PC4   apprenant
echo.
echo    Ne pas fermer cette fenetre : elle est le serveur.
echo    Arret : Ctrl+C.
echo =====================================================================
echo.
echo    Compilation puis demarrage. Au premier lancement, Maven se telecharge
echo    (~9 Mo) puis les dependances : cela peut prendre quelques minutes.
echo    Les lignes [INFO] qui defilent sont normales.
echo    Si rien ne s'affiche en 5 minutes, lancer postes\verifier.cmd
echo    (diagnostic en 20 secondes) et lire le message affiche.
echo.

call "%MVN%" -B javafx:run "-Djavafx.args=--role=serveur --poste=PC1 --port=%PORT%"
if errorlevel 1 (
    echo.
    echo [ERREUR] Le serveur n'a pas demarre. Voir le message ci-dessus.
    pause
) else (
    echo.
    echo Le serveur est arrete. La fenetre reste ouverte pour lire le journal.
    pause
)
endlocal
