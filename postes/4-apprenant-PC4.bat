@echo off
REM Compatibilite double-clic .bat : le role reste fixe dans le lanceur .cmd.
call "%~dp04-apprenant-PC4.cmd"
exit /b %errorlevel%
