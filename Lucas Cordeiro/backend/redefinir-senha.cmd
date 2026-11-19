@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd compile exec:java "-Dexec.args=--redefinir-senha"
exit /b %ERRORLEVEL%
