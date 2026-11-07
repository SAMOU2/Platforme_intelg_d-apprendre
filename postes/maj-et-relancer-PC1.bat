@echo off
REM Compatibilite double-clic .bat : le travail reel est dans le .cmd.
call "%~dp0maj-et-relancer-PC1.cmd"
exit /b %errorlevel%
