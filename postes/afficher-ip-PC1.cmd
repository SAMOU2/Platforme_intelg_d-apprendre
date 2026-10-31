@echo off
setlocal
echo.
echo =====================================================================
echo   ADRESSE DU SERVEUR A SAISIR SUR LES POSTES CLIENTS
REM Le script PowerShell affiche des IP uniquement, pas les secrets serveur.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0afficher-ip-PC1.ps1"
if errorlevel 1 (
    echo [ATTENTION] Detection automatique des IP impossible.
    echo Ouvrir ipconfig et relever l'adresse IPv4 de la carte Wi-Fi ou Ethernet active.
)
echo   Saisir seulement l'IP dans l'interface, sans protocole ni port.
echo   Salle : 7777 TCP/UDP. Authentification : 8443 TCP par defaut.
echo   Ces adresses ne prouvent pas que les services sont deja demarres.
echo =====================================================================
endlocal
exit /b 0
