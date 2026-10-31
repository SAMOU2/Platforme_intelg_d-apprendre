@echo off
REM Compatibilite double-clic .bat : le role reste fixe dans le lanceur .cmd.
call "%~dp02-administrateur-PC2.cmd"
exit /b %errorlevel%
