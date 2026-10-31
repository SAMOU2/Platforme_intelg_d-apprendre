@echo off
chcp 65001 >nul
title IHM 2026 - sonde des animations
setlocal
cd /d "%~dp0.."

if not exist "outils\sonde-animations.java" (
    echo [ERREUR] outils\sonde-animations.java est introuvable.
    goto :echec
)

call "%~dp0..\postes\environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

echo.
echo Compilation du projet et preparation des dependances...
call "%MVN%" -B -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target\sonde-animations-classpath.txt"
if errorlevel 1 (
    echo [ERREUR] Maven n'a pas pu compiler le projet ou preparer le classpath.
    goto :echec
)

if not exist "target\classes\fr\ihm2026\App.class" (
    echo [ERREUR] La classe principale de l'application est absente apres compilation.
    goto :echec
)
if not exist "target\sonde-animations-classpath.txt" (
    echo [ERREUR] Maven n'a pas genere le classpath de la sonde.
    goto :echec
)

set "DEPENDENCIES="
set /p "DEPENDENCIES="<"target\sonde-animations-classpath.txt"
if not defined DEPENDENCIES (
    echo [ERREUR] Le classpath des dependances est vide.
    goto :echec
)

echo.
echo Lancement de la sonde au ralenti (echelle x10)...
"%JAVA_HOME%\bin\java.exe" -Dihm.animations.echelle=10 -cp "%CD%\target\classes;%DEPENDENCIES%" "%CD%\outils\sonde-animations.java"
set "RESULTAT=%ERRORLEVEL%"
if not "%RESULTAT%"=="0" (
    echo.
    echo [ERREUR] La sonde s'est terminee avec le code %RESULTAT%.
    goto :echec
)

echo.
echo Sonde terminee.
if not defined IHM_NO_PAUSE pause
endlocal
exit /b 0

:echec
if not defined IHM_NO_PAUSE pause
endlocal
exit /b 1
