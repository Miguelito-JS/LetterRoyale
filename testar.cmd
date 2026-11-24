@echo off
REM Roda os testes automatizados (JUnit) do back-end
cd /d "%~dp0"
set "JAVA_HOME=%~dp0oracleJdk-27"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call mvn -f backend\pom.xml test
pause
