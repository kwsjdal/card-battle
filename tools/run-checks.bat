@echo off
REM ============================================================
REM  Card Battle System - One-Click Regression Suite
REM
REM  Why: the offline maven env has no surefire plugin, so JUnit cannot run.
REM       These are standalone main() classes executed with javac + java.
REM  Pre-step: run "mvn -o compile" first so target\classes is up to date.
REM ============================================================
setlocal
REM force UTF-8 on stdout so Chinese labels survive redirection
chcp 65001 >nul

set MJ=C:\Users\hp\.m2\repository\com\baomidou\mybatis-plus-annotation\3.5.3.2\mybatis-plus-annotation-3.5.3.2.jar
set MY=C:\Users\hp\.m2\repository\org\mybatis\mybatis\3.5.13\mybatis-3.5.13.jar
set CP=target\classes;%MJ%;%MY%

echo ============================================================
echo  [1/2] AbilityCheck : battle abilities
echo        taunt / shield / charge / summoning sickness /
echo        deathrattle / battlecry / lifesteal
echo ============================================================
javac -encoding UTF-8 -cp "%CP%" -d tools\out tools\AbilityCheck.java
if errorlevel 1 goto :compilefail
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "%CP%;tools\out" AbilityCheck
set AB=%ERRORLEVEL%

echo.
echo ============================================================
echo  [2/2] DeckCodeCheck : deck share code / hero power /
echo        fatigue / spectator data masking
echo ============================================================
javac -encoding UTF-8 -cp "%CP%" -d tools\out tools\DeckCodeCheck.java
if errorlevel 1 goto :compilefail
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "%CP%;tools\out" DeckCodeCheck
set DC=%ERRORLEVEL%

echo.
echo ============================================================
echo  [3/3] WsE2E : live end-to-end over WebSocket
echo        plays a full bot game, attacks it from another
echo        account, checks spectator masking and replay.
echo        Requires the backend already running on :8080.
echo ============================================================
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if errorlevel 1 goto :nobackend
if not exist frontend\node_modules\@stomp\stompjs goto :nostomp

set NODE_PATH=%CD%\frontend\node_modules
node tools\ws-e2e.cjs
set E2E=%ERRORLEVEL%
goto :summary

:nobackend
echo  SKIPPED: no backend on :8080 - run "mvn -o spring-boot:run" first
set E2E=skip
goto :summary

:nostomp
echo  SKIPPED: frontend deps missing - run "npm install" in frontend\
set E2E=skip
goto :summary

:summary
echo.
echo ============================================================
if "%AB%"=="0" if "%DC%"=="0" if "%E2E%"=="0" (
  echo  RESULT: ALL REGRESSION CHECKS PASSED
) else (
  echo  RESULT: AB=%AB%  DC=%DC%  E2E=%E2E%
  echo           (0 = passed / skip = not run / other = failed)
)
echo ============================================================
endlocal
exit /b

:compilefail
echo.
echo COMPILE FAILED - run "mvn -o compile" to build target\classes first
endlocal
exit /b 1
