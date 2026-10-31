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

echo Ce lanceur ne permet pas de choisir un role.
echo Utilisez uniquement le script correspondant au poste attribue :
echo.
echo    Serveur        : postes\1-serveur-PC1.cmd
echo    Administrateur : postes\2-administrateur-PC2.cmd
echo    Enseignant     : postes\3-enseignant-PC3.cmd
echo    Apprenant      : postes\4-apprenant-PC4.cmd
echo.
echo Chaque poste humain doit rejoindre le serveur avant d'ouvrir son interface.
echo Le role configure dans un script de poste n'est pas une authentification :
echo une vraie securite necessite un controle des comptes cote serveur.
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
