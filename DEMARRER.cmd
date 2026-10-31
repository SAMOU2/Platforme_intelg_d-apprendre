@echo off
chcp 65001 >nul
title IHM 2026 - demarrage
setlocal
cd /d "%~dp0"

echo =====================================================================
echo    IHM 2026 - DEMARRAGE DU POSTE
echo =====================================================================
echo.

if not exist "pom.xml" goto :incomplet
if not exist "mvnw.cmd" goto :incomplet
if not exist "postes\1-serveur-PC1.cmd" goto :incomplet
if not exist "postes\2-administrateur-PC2.cmd" goto :incomplet
if not exist "postes\3-enseignant-PC3.cmd" goto :incomplet
if not exist "postes\4-apprenant-PC4.cmd" goto :incomplet
if not exist "postes\environnement.cmd" goto :incomplet
if not exist "postes\authentification-PC1.bat" goto :incomplet

echo Ce lanceur ne permet pas de choisir un role.
echo Utilisez uniquement le script correspondant au poste attribue :
echo.
echo    PC1 serveur salle : postes\1-serveur-PC1.bat
echo    PC1 authentification : postes\authentification-PC1.bat
echo    Sur PC1 uniquement : demarrer Docker/PostgreSQL, puis ces deux services.
echo    Administrateur : postes\2-administrateur-PC2.cmd
echo    Enseignant     : postes\3-enseignant-PC3.cmd
echo    Apprenant      : postes\4-apprenant-PC4.cmd
echo.
echo Clients : pas de Docker, pas de PostgreSQL local.
echo Saisir l'IP de PC1 dans l'interface, ou definir IHM_SERVEUR.
echo Chaque poste humain doit rejoindre le serveur avant d'ouvrir son interface.
echo Le role configure dans un script de poste n'est pas une authentification :
echo le service HTTPS verifie le compte et son role cote serveur.
echo.
echo Pour diagnostiquer un probleme de lancement :
echo    postes\verifier.cmd
echo.
pause
exit /b 0

:incomplet
echo [PROBLEME] Dossier incomplet : pom.xml, mvnw.cmd ou un script poste est manquant.
echo Extraire le projet complet, puis relancer DEMARRER.cmd.
echo.
pause
exit /b 1
