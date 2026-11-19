@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0backend\iniciar-site.ps1"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
    echo Nao foi possivel iniciar o DogMania.
    pause
)
exit /b %EXIT_CODE%
