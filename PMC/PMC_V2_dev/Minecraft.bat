@echo off
cd /d "%~dp0"
"%~dp0jre\bin\javaw.exe" --module-path "%~dp0game\sklauncher\javafx" --add-modules javafx.controls,javafx.graphics -jar "%~dp0PMC-Launcher.jar"
