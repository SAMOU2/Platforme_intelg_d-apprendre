@echo off
REM Compatibilite double-clic .bat : le role reste fixe dans le lanceur .cmd.
call "%~dp01-serveur-PC1.cmd"
exit /b %errorlevel%
