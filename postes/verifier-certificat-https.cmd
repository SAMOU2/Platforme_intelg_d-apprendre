@echo off
chcp 65001 >nul
REM =====================================================================
REM  DIAGNOSTIC - accessibilite du certificat HTTPS d'authentification.
REM
REM  A lancer quand PC4 affiche « Service HTTPS d'authentification
REM  inaccessible » ou un message de certificat. Il n'ouvre aucune
REM  session, ne modifie aucun magasin et n'ecrit aucun secret : il lit.
REM
REM  Il affiche :
REM    [1] l'autorite privee presente dans le dossier postes\secrets
REM    [2] si cette autorite est approuvee dans le JDK qui lance l'app
REM    [3] le certificat reellement presente par le serveur, sa validite
REM        et la liste des adresses couvertes (SAN)
REM    [4] si le JDK fait confiance a ce certificat a l'adresse saisie
REM
REM  Double-clic, ou :  postes\verifier-certificat-https.cmd
REM  Copier tout le texte affiche pour analyse.
REM =====================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
call "%~dp0environnement.cmd"
if not "%ENV_OK%"=="1" goto :fin

echo =====================================================================
echo   VERIFICATION DU CERTIFICAT HTTPS
echo =====================================================================
echo.

echo [1] Autorite privee du projet ^(postes\secrets\autorite-ihm.crt^)
if exist "postes\secrets\autorite-ihm.crt" (
    "%JAVA_HOME%\bin\keytool.exe" -printcert -file "postes\secrets\autorite-ihm.crt"
    echo.
    echo     Empreinte a comparer avec celle lue sur PC1. Ne jamais copier le .p12.
) else (
    echo     Fichier absent ici : normal sur un poste client.
    echo     Le responsable PC1 fournit uniquement autorite-ihm.crt ^(public^).
)
echo.

echo [2] Autorite deja approuvee dans CE JDK ?
echo     Magasin : %JAVA_HOME%\lib\security\cacerts
"%JAVA_HOME%\bin\keytool.exe" -list -cacerts -storepass changeit 2>nul | findstr /i "plateforme-ca"
if errorlevel 1 (
    echo     Aucune entree plateforme-ca-* : lancer postes\approuver-autorite-client.bat
    echo     avec CE JDK, puis relancer ce diagnostic.
)
echo.

echo [3] Adresse et port du serveur d'authentification
set "PORTAUTO="
if exist ".ihm-runtime\https.port" (
    set /p LIGNE=<".ihm-runtime\https.port"
    for /f "tokens=1" %%P in ("!LIGNE!") do set "PORTAUTO=%%P"
)
if defined PORTAUTO (
    echo     Port HTTPS en cours detecte : !PORTAUTO!
    echo     ^(ne l'utiliser que si la fenetre d'authentification est ouverte^)
)
set "ADRESSE="
set /p "ADRESSE=Adresse IP du serveur PC1 (ex. 192.168.8.100) : "
if not defined ADRESSE (
    echo [ERREUR] Adresse vide : rien a verifier.
    goto :fin
)
set "PORT="
if defined PORTAUTO (
    set /p "PORT=Port HTTPS [!PORTAUTO!] : "
) else (
    set /p "PORT=Port HTTPS affiche par la fenetre d'authentification : "
)
if not defined PORT if defined PORTAUTO set "PORT=!PORTAUTO!"
if not defined PORT (
    echo [ERREUR] Port vide : relancer la fenetre d'authentification et lire son port.
    goto :fin
)
echo.

echo [4] Test de connexion et lecture du certificat
echo ---------------------------------------------------------------------
"%JAVA_HOME%\bin\java.exe" "postes\diagnostic\VerificateurCertificatHttps.java" !ADRESSE! !PORT!
echo ---------------------------------------------------------------------
echo.

:fin
echo Copier tout ce texte ^(clic droit ^> Selectionner, puis Entree^) pour analyse.
pause
endlocal
