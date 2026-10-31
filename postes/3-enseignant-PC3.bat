@echo off
REM Compatibilite double-clic .bat : le role reste fixe dans le lanceur .cmd.
call "%~dp03-enseignant-PC3.cmd"
exit /b %errorlevel%
