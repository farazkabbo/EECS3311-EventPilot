@echo off
rem EventPilot launcher (Windows). No arguments = GUI, otherwise CLI, e.g.  eventpilot event list
setlocal
set JAR=%~dp0target\eventpilot.jar
if not exist "%JAR%" (
  echo Building EventPilot (first run^)...
  call "%~dp0mvnw.cmd" -q -DskipTests package || exit /b 1
)
java -jar "%JAR%" %*
