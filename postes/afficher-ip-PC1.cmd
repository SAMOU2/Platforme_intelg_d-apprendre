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
echo   Salle : 7777 TCP/UDP. Authentification : port dynamique.
set "PORTHTTPS="
if exist "%~dp0..\.ihm-runtime\https.port" set /p PORTHTTPS=<"%~dp0..\.ihm-runtime\https.port"
for /f "tokens=1" %%P in ("%PORTHTTPS%") do set "PORTHTTPS=%%P"
if defined PORTHTTPS (
    echo   Port HTTPS annonce en ce moment : %PORTHTTPS%
) else (
    echo   Port HTTPS : aucun annonce. La fenetre d'authentification doit rester
    echo   ouverte. Port prefere : 28443. La valeur 8443 n'est qu'un repli obsolete.
)
echo   Ces adresses ne prouvent pas que les services sont deja demarres.
echo =====================================================================
endlocal
exit /b 0
