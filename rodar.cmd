@echo off
REM =====================================================================
REM  RODAR O LETTERROYALE COM O CODIGO NOVO
REM  1) compila e gera o .jar novo   2) copia o .jar para fora da pasta target
REM  3) sobe o servidor a partir da copia
REM  Por que a copia? O VS Code recompila a pasta target sozinho enquanto voce
REM  edita. Se o servidor rodasse de dentro dela, as classes sumiam no meio do
REM  uso e o site passava a responder "Erro no servidor.".
REM =====================================================================
cd /d "%~dp0"
set "JAVA_HOME=%~dp0oracleJdk-27"
set "PATH=%JAVA_HOME%\bin;%PATH%"
where mvn >nul 2>nul || (echo [ERRO] Maven nao encontrado. Instale o Maven e rode de novo. & pause & exit /b 1)

REM Servidor antigo ainda ligado na porta 8080? Se for Java, fecha ele antes de subir o novo.
set "OCUPADA="
set "FECHADO="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":8080 .*LISTENING"') do call :fechar %%P
if defined OCUPADA (
  echo [ERRO] A porta 8080 esta ocupada por outro programa ^(PID %OCUPADA%^), que nao e Java. Feche ele e rode de novo.
  pause & exit /b 1
)

echo Compilando o LetterRoyale...
call mvn -q -f backend\pom.xml -DskipTests clean package
if errorlevel 1 (echo [ERRO] A compilacao falhou. Veja a mensagem acima. & pause & exit /b 1)

set "DESTINO=%LOCALAPPDATA%\LetterRoyale"
if not exist "%DESTINO%" mkdir "%DESTINO%"
copy /y "backend\target\letterroyale-backend-1.0.0.jar" "%DESTINO%\servidor.jar" >nul
if errorlevel 1 (echo [ERRO] Nao consegui copiar o .jar. Feche outro servidor aberto e rode de novo. & pause & exit /b 1)

echo Subindo... quando aparecer "Started LetterRoyaleApplication", abra http://localhost:8080
cd backend
java -jar "%DESTINO%\servidor.jar"
pause
exit /b

:fechar
if "%~1"=="0" exit /b
if "%~1"=="%FECHADO%" exit /b
for /f "tokens=1" %%N in ('tasklist /FI "PID eq %~1" /NH') do (
  if /I "%%N"=="java.exe" ( echo Fechando o servidor antigo que estava na porta 8080 ^(PID %~1^)... & set "FECHADO=%~1" & taskkill /PID %~1 /F >nul & timeout /t 2 /nobreak >nul & exit /b )
  if /I "%%N"=="javaw.exe" ( echo Fechando o servidor antigo que estava na porta 8080 ^(PID %~1^)... & set "FECHADO=%~1" & taskkill /PID %~1 /F >nul & timeout /t 2 /nobreak >nul & exit /b )
)
set "OCUPADA=%~1"
exit /b
