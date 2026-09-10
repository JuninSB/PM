@echo off
cd /d "%~dp0"
start "" /b "%~dp0main\bin\javaw.exe" --module-path "%~dp0game\sklauncher\javafx" --add-modules javafx.controls,javafx.graphics -jar "%~dp0main\PMC-Launcher.jar"
exit /b
