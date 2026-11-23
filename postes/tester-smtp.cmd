@echo off
chcp 65001 >nul
title IHM 2026 - test du serveur SMTP
setlocal
cd /d "%~dp0.."

REM =====================================================================
REM  Envoie un vrai message de test avec la configuration de
REM  postes\configuration-PC1.local.cmd. Aucun mot de passe n'est affiche.
REM
REM  A executer sur PC1 quand aucun code (PIN, connexion, mot de passe
REM  oublie) n'arrive : ce script distingue une configuration incomplete,
REM  un refus d'authentification et un reseau injoignable.
REM
REM  Usage : postes\tester-smtp.cmd votre-adresse@exemple.org
REM =====================================================================

if "%~1"=="" (
    echo [ERREUR] Indiquer l'adresse qui doit recevoir le message de test.
    echo          Exemple : postes\tester-smtp.cmd votre-adresse@exemple.org
    echo.
    pause
    exit /b 1
)

if exist "%~dp0configuration-PC1.local.cmd" (
    call "%~dp0configuration-PC1.local.cmd"
) else (
    echo [ATTENTION] postes\configuration-PC1.local.cmd est absent :
    echo             seules les variables de l'environnement Windows seront utilisees.
)

call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

set "CONFIG_MANQUANTE="
for %%V in (IHM_SMTP_HOST IHM_SMTP_FROM) do (
    if not defined %%V (
        echo [ERREUR] Variable obligatoire absente : %%V
        set "CONFIG_MANQUANTE=1"
    )
)
if defined CONFIG_MANQUANTE (
    echo.
    echo Definir IHM_SMTP_HOST et IHM_SMTP_FROM dans postes\configuration-PC1.local.cmd.
    echo Ajouter IHM_SMTP_USER et IHM_SMTP_PASSWORD si le serveur exige une authentification.
    echo.
    pause
    exit /b 1
)

if defined IHM_SMTP_USER (
    if not defined IHM_SMTP_PASSWORD (
        echo [ERREUR] IHM_SMTP_USER est defini sans IHM_SMTP_PASSWORD.
        echo          Renseigner les deux, ou aucun des deux.
        pause
        exit /b 1
    )
)

echo.
echo Serveur SMTP : %IHM_SMTP_HOST% port %IHM_SMTP_PORT%
if defined IHM_SMTP_SSL echo Mode TLS force : IHM_SMTP_SSL=%IHM_SMTP_SSL%
if defined IHM_SMTP_USER (echo Authentification : activee) else (echo Authentification : desactivee)
echo.

echo Compilation du projet et preparation des dependances...
call "%MVN%" -B -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target\smtp-classpath.txt"
if errorlevel 1 (
    echo [ERREUR] Maven n'a pas pu compiler le projet ou preparer le classpath.
    goto :echec
)

set "DEPENDANCES="
set /p "DEPENDANCES="<"target\smtp-classpath.txt"
if not defined DEPENDANCES (
    echo [ERREUR] Le classpath des dependances est vide.
    goto :echec
)

REM Compiler explicitement puis executer : le mode "java Fichier.java" compile avec le
REM classpath mais n'execute pas avec la meme liste de jars (jakarta.mail manquait au runtime).
set "CLASSE_TEST=%CD%\target\smtp-test-classes"
if exist "%CLASSE_TEST%" rd /s /q "%CLASSE_TEST%"
mkdir "%CLASSE_TEST%" 2>nul
echo Compilation de l'outil de test...
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "%CD%\target\classes;%DEPENDANCES%" -d "%CLASSE_TEST%" "%~dp0TesterSmtp.java"
if errorlevel 1 (
    echo [ERREUR] Compilation de TesterSmtp.java impossible. Un message javac explique la cause ci-dessus.
    goto :echec
)

echo Envoi du message de test a %~1 ...
echo.
"%JAVA_HOME%\bin\java.exe" -cp "%CLASSE_TEST%;%CD%\target\classes;%DEPENDANCES%" TesterSmtp "%~1"
set "RESULTAT=%ERRORLEVEL%"
echo.
if not "%RESULTAT%"=="0" (
    echo [ECHEC] Le message n'a pas pu etre envoye. Lire la cause affichee ci-dessus.
    goto :echec
)
echo [OK] Message accepte par le serveur SMTP. Verifier la boite de reception
echo      et les courriers indesirables de %~1.
if not defined IHM_NO_PAUSE pause
endlocal
exit /b 0

:echec
if not defined IHM_NO_PAUSE pause
endlocal
exit /b 1
