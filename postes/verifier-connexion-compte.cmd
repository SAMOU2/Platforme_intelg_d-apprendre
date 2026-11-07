@echo off
chcp 65001 >nul
REM =====================================================================
REM  DIAGNOSTIC - verifier qu'un compte se connecte, sans l'interface.
REM
REM  Ce script envoie la MEME requete que l'ecran de connexion
REM  (POST /api/auth/connexion) et affiche la reponse du serveur :
REM   HTTP 200 + jeton        -> session ouverte
REM   200 + identifiant       -> code envoye par e-mail, compte valide
REM   401                     -> e-mail ou mot de passe refuse
REM   403                     -> compte non active / non accepte
REM   erreur de connexion     -> port, service ou pare-feu
REM
REM  Le mot de passe est demande sans affichage, transmis directement au
REM  programme Java : il n'est jamais ecrit sur le disque, jamais affiche,
REM  jamais passe en argument de ligne de commande.
REM =====================================================================
setlocal DisableDelayedExpansion
cd /d "%~dp0.."
call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" exit /b 1

echo =====================================================================
echo   VERIFICATION DE CONNEXION D'UN COMPTE
echo =====================================================================
echo.

set "PORTAUTO="
if exist ".ihm-runtime\https.port" set /p LIGNE=<".ihm-runtime\https.port"
for /f "tokens=1" %%P in ("%LIGNE%") do set "PORTAUTO=%%P"
if defined PORTAUTO echo     Port HTTPS annonce en ce moment : %PORTAUTO%

set "HOTE="
set /p "HOTE=Adresse du serveur (ex. 192.168.8.100) : "
if not defined HOTE (
    echo [ERREUR] Adresse vide : rien a verifier.
    pause
    exit /b 1
)
set "PORT="
if defined PORTAUTO (
    set /p "PORT=Port HTTPS [%PORTAUTO%] : "
) else (
    set /p "PORT=Port HTTPS affiche par la fenetre d'authentification : "
)
if not defined PORT set "PORT=%PORTAUTO%"
if not defined PORT (
    echo [ERREUR] Port vide : relancer la fenetre d'authentification et lire son port.
    pause
    exit /b 1
)

if not exist "target\classes\fr\ihm2026\api\service\ConfiancePlateforme.class" (
    echo Compilation necessaire...
    call "%MVN%" -B -DskipTests compile
    if errorlevel 1 (
        echo [ERREUR] Compilation impossible : lire les messages ci-dessus.
        pause
        exit /b 1
    )
)

echo.
echo Saisie des identifiants ^(le mot de passe ne s'affiche pas^) :
echo ---------------------------------------------------------------------
powershell.exe -NoProfile -Command "$courriel = Read-Host 'E-mail'; $protec = Read-Host 'Mot de passe' -AsSecureString; $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($protec); try { $clair = [Runtime.InteropServices.Marshal]::PtrToStringAuto($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }; Write-Output $courriel; Write-Output $clair; $clair = $null; [GC]::Collect()" | "%JAVA_HOME%\bin\java.exe" -cp "target\classes" "postes\diagnostic\VerifierConnexionCompte.java" %HOTE% %PORT%
echo ---------------------------------------------------------------------
echo.
pause
endlocal
