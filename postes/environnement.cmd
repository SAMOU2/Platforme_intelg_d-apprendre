@echo off
chcp 65001 >nul
REM =====================================================================
REM  Environnement commun des fichiers de lancement des postes.
REM
REM  Role : trouver un JDK 25 LTS et le Maven Wrapper du projet. Appele par
REM  les quatre scripts de poste (pas de setlocal ici : les variables
REM  JAVA_HOME, PATH, MVN et ENV_OK doivent rester visibles chez l'appelant).
REM
REM  Regle : aucun chemin d'echec ne sort en silence. Chaque erreur pose
REM  ENV_OK=0 et affiche la cause, sinon la fenetre se referme aussitot et
REM  l'utilisateur voit seulement " ca n'ouvre rien ".
REM =====================================================================

set "ENV_OK=0"

REM 1) Le projet est-il la ? Un .cmd lance depuis l'apercu d'un zip, ou
REM    depuis un dossier postes\ copie seul, ne trouve pas le projet.
if not exist "%~dp0..\pom.xml" (
    echo [ERREUR] pom.xml introuvable : le dossier du projet est incomplet.
    echo          Ce script doit rester dans  ^<projet^>\postes\.
    echo          Decompresser tout le zip, puis double-cliquer DEMARRER.cmd
    echo          a la racine du projet.
    goto :echec
)

REM 2) JDK : utiliser JAVA_HOME s'il pointe vers un JDK. Sinon chercher
REM    javac dans le PATH, puis essayer l'installation Liberica 25 connue.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" goto :jdk_trouve
)
for /f "delims=" %%J in ('where javac 2^>nul') do (
    for %%K in ("%%~dpJ..") do set "JAVA_HOME=%%~fK"
    goto :jdk_trouve
)
set "JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-25-Full"
if exist "%JAVA_HOME%\bin\javac.exe" goto :jdk_trouve
echo [ERREUR] Aucun JDK trouvé dans JAVA_HOME, le PATH ou l'emplacement Liberica attendu.
echo          Installer un JDK 25 LTS, puis définir JAVA_HOME
echo          ou ajouter son dossier bin au PATH.
goto :echec

:jdk_trouve
set "JAVA_MAJOR="
for /f "tokens=2" %%V in ('"%JAVA_HOME%\bin\javac.exe" -version 2^>^&1') do set "JAVA_MAJOR=%%V"
for /f "tokens=1 delims=." %%V in ("%JAVA_MAJOR%") do set "JAVA_MAJOR=%%V"
if not defined JAVA_MAJOR (
    echo [ERREUR] Impossible de lire la version de javac dans "%JAVA_HOME%".
    goto :echec
)
if not "%JAVA_MAJOR%"=="25" (
    echo [ERREUR] JDK %JAVA_MAJOR% détecté ; ce projet nécessite Java 25 LTS.
    echo          Installer un JDK 25 LTS et définir JAVA_HOME sur son dossier.
    goto :echec
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JDK    : %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1

REM 3) Maven : installation locale si elle existe, sinon le wrapper du
REM    projet (mvnw.cmd telecharge Maven 3.9.9 au premier lancement).
where mvn >nul 2>&1
if errorlevel 1 (
    if not exist "%~dp0..\mvnw.cmd" (
        echo [ERREUR] ni "mvn" ni "mvnw.cmd" introuvable dans le projet.
        goto :echec
    )
    set "MVN=%~dp0..\mvnw.cmd"
    REM Le wrapper (Maven Wrapper 3.3+) est muet par defaut : on lui demande d'expliquer
    REM ce qu'il fait. Sans cela, un premier lancement (telechargement de Maven, ~9 Mo)
    REM laisserait la fenetre vide pendant plusieurs minutes, ce qui passe pour un blocage.
    set "MVNW_VERBOSE=true"
    echo Maven  : wrapper du projet ^(mvnw.cmd^) - premiere utilisation : telechargement
) else (
    set "MVN=mvn"
    echo Maven  : installation locale
)
echo Projet : %CD%
set "ENV_OK=1"
exit /b 0

:echec
echo.
echo Ce message restera affiche : appuyer sur une touche pour fermer.
pause
exit /b 1
