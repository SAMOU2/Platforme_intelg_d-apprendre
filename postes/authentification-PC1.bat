@echo off
chcp 65001 >nul
title IHM 2026 - service HTTPS d'authentification
setlocal DisableDelayedExpansion
cd /d "%~dp0.."

REM Fichier local facultatif, jamais versionne : environnement herite sinon.
if exist "%~dp0configuration-PC1.local.cmd" call "%~dp0configuration-PC1.local.cmd"

call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

REM Afficher les noms manquants, jamais la valeur des secrets.
set "CONFIG_MANQUANTE="
for %%V in (IHM_DB_URL IHM_DB_USER IHM_DB_PASSWORD IHM_TLS_KEYSTORE IHM_TLS_PASSWORD IHM_SMTP_HOST IHM_SMTP_FROM) do (
    if not defined %%V (
        echo [ERREUR] Variable obligatoire absente : %%V
        set "CONFIG_MANQUANTE=1"
    )
)
if defined CONFIG_MANQUANTE goto :configuration_manquante
if not exist "%IHM_TLS_KEYSTORE%" (
    echo [ERREUR] Le fichier indique par IHM_TLS_KEYSTORE est introuvable.
    echo          Utiliser de preference un chemin absolu.
    goto :echec
)

if not defined IHM_AUTH_PORT set "IHM_AUTH_PORT=8443"

echo.
echo PC1 uniquement : Docker et PostgreSQL doivent etre disponibles ici.
echo Les clients n'ont besoin ni de Docker ni des secrets de ce serveur.
echo Compilation du service d'authentification HTTPS...
call "%MVN%" -B -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target\authentification-classpath.txt"
if errorlevel 1 (
    echo [ERREUR] Maven n'a pas pu compiler le service ou preparer ses dependances.
    goto :echec
)
if not exist "target\classes\fr\ihm2026\api\service\ServeurAuthentificationHttps.class" (
    echo [ERREUR] La classe du service HTTPS est absente apres compilation.
    goto :echec
)
if not exist "target\authentification-classpath.txt" (
    echo [ERREUR] Maven n'a pas genere le classpath du service.
    goto :echec
)

set "DEPENDENCIES="
set /p "DEPENDENCIES="<"target\authentification-classpath.txt"
if not defined DEPENDENCIES (
    echo [ERREUR] Le classpath des dependances est vide.
    goto :echec
)

echo.
echo Demarrage du service HTTPS sur le port %IHM_AUTH_PORT% (8443 par defaut).
echo Garder cette fenetre ouverte. Arret : Ctrl+C.
echo.
"%JAVA_HOME%\bin\java.exe" -cp "%CD%\target\classes;%DEPENDENCIES%" fr.ihm2026.api.service.ServeurAuthentificationHttps
if errorlevel 1 (
    echo.
    echo [ERREUR] Le service HTTPS s'est arrete avec une erreur.
    goto :echec
)
endlocal
exit /b 0

:configuration_manquante
echo [ERREUR] Configuration serveur absente.
echo Copier postes\configuration-PC1.exemple.cmd vers postes\configuration-PC1.local.cmd
echo puis renseigner les parametres dans le fichier local. Il est exclu de Git.
echo Les variables peuvent aussi etre definies dans l'environnement Windows.
echo Definir IHM_DB_URL, IHM_DB_USER,
echo          IHM_DB_PASSWORD, IHM_TLS_KEYSTORE, IHM_TLS_PASSWORD,
echo          IHM_SMTP_HOST et IHM_SMTP_FROM avant le lancement.
echo          Ne pas inscrire les mots de passe dans ce fichier.
echo          Si aucun administrateur n'existe, définir aussi les variables IHM_BOOTSTRAP_ADMIN_NAME,
echo          IHM_BOOTSTRAP_ADMIN_EMAIL et IHM_BOOTSTRAP_ADMIN_PASSWORD.
echo          Ajouter IHM_SMTP_USER et IHM_SMTP_PASSWORD si le serveur SMTP exige une authentification.
goto :echec

:echec
echo.
pause
endlocal
exit /b 1
