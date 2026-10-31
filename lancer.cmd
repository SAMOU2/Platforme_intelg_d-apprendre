@echo off
chcp 65001 >nul
title IHM 2026 - lancement generique
setlocal
cd /d "%~dp0"
call "%~dp0postes\environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

echo.
echo Ce lancement generique ne configure aucun role.
echo Pour ouvrir un poste, utilisez le script attribue dans le dossier postes.
echo Les roles humains necessitent une connexion au serveur.
echo.
echo Demarrage de l'ecran de configuration. Aucun acces metier ne sera ouvert.
echo Maven affiche sa progression ; au premier lancement, le telechargement peut durer quelques minutes.
echo.
call "%MVN%" -B javafx:run
if errorlevel 1 (
    echo.
    echo [ERREUR] Le lancement a echoue. Lire le message affiche plus haut.
    pause
    endlocal
    exit /b 1
)
echo.
echo L'application est fermee.
pause
endlocal
