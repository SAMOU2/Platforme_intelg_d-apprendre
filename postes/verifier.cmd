@echo off
chcp 65001 >nul
REM =====================================================================
REM  DIAGNOSTIC - a lancer quand une fenetre de poste reste muette.
REM
REM  Ce script ne lance pas l'application : il affiche, en une vingtaine
REM  de secondes, tout ce dont on a besoin pour comprendre pourquoi un
REM  poste ne demarre pas (JDK, Maven, dossier du projet, telechargement
REM  du wrapper). En cas de probleme : copier tout le texte affiche.
REM
REM  Double-clic, ou :  postes\verifier.cmd
REM =====================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

echo =====================================================================
echo   DIAGNOSTIC IHM 2026
echo =====================================================================
echo.
echo [1] Dossier du projet (celui d'ou part le lancement)
echo     %CD%
if exist "pom.xml" (echo     pom.xml      : present) else (echo     pom.xml      : ABSENT - le script n'est pas dans le bon dossier)
if exist "mvnw.cmd" (echo     mvnw.cmd     : present) else (echo     mvnw.cmd     : ABSENT)
if exist "src\main\java" (echo     source Java  : present) else (echo     source Java  : ABSENT)
echo.

echo [2] JDK
echo     JAVA_HOME = %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1
echo.

echo [3] Maven
where mvn >nul 2>&1
if errorlevel 1 (echo     mvn sur le PATH : non - le projet utilisera son wrapper) else (echo     mvn sur le PATH : oui)
if exist "%USERPROFILE%\.m2\wrapper\dists" (
    echo     Maven deja telecharge :
    dir /b "%USERPROFILE%\.m2\wrapper\dists"
) else (
    echo     Maven du wrapper : pas encore telecharge ^(le 1er lancement le fera, ~9 Mo^)
)
echo.

echo [4] Demarrage de Maven par le wrapper (c'est ici que l'on voit s'il bloque)
echo ---------------------------------------------------------------------
set "MVNW_VERBOSE=true"
call mvnw.cmd -v
echo ---------------------------------------------------------------------
echo.
echo [5] Conclusion
echo     - Si l'etape 4 affiche " Apache Maven 3.9.9 " : Maven fonctionne, le poste
echo       peut demarrer. Relancer le script du poste et attendre la banniere.
echo     - Si l'etape 4 affiche une erreur de telechargement : pas d'acces reseau
echo       (proxy, antivirus). Installer Maven a la main et l'ajouter au PATH.
echo     - Si l'etape 4 reste muette : le wrapper attend PowerShell. Lancer
echo       " powershell -Command Get-Date " pour verifier, puis relancer.
echo.
pause
endlocal
