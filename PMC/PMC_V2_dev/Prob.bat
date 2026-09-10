@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

for /f "delims=" %%A in ('echo prompt $E^| cmd') do set "ESC=%%A"
set "C_RESET=!ESC![0m"
set "C_CYAN=!ESC![96m"
set "C_BLUE=!ESC![94m"
set "C_GREEN=!ESC![92m"
set "C_YELLOW=!ESC![93m"
set "C_RED=!ESC![91m"
set "C_WHITE=!ESC![97m"
set "C_GRAY=!ESC![90m"

title PMC V3 - Portable Minecraft

mode con cols=100 lines=32 >nul 2>&1
color 07
cls

echo.
echo !C_CYAN!╔══════════════════════════════════════════════════════════════════════════════════════════╗!C_RESET!
echo !C_CYAN!║!C_WHITE!                            PMC V3 - PORTABLE MINECRAFT                            !C_CYAN!║!C_RESET!
echo !C_CYAN!╠══════════════════════════════════════════════════════════════════════════════════════════╣!C_RESET!
echo !C_CYAN!║!C_GRAY!  Inicializador portátil                                                       !C_CYAN!║!C_RESET!
echo !C_CYAN!╚══════════════════════════════════════════════════════════════════════════════════════════╝!C_RESET!
echo.

set "ROOT=%~dp0"
set "JAVA=%~dp0jre\bin\java.exe"
set "JAVA_VERSION=Desconhecido"
set "SK=%~dp0SKlauncher.jar"
set "GAME=%~dp0game"
set "LOG=%~dp0game\sklauncher\sklauncher_logs.txt"

echo !C_BLUE![ PMC ]!C_RESET! Verificando arquivos locais...
echo.

if not exist "%JAVA%" (
    echo !C_RED![ ERRO ]!C_RESET! Java portátil não encontrado.
    echo.
    echo Caminho esperado:
    echo "%JAVA%"
    echo.
    pause
    exit /b 1
)

echo !C_GREEN![  OK  ]!C_RESET! Runtime Java encontrado.

if not exist "%SK%" (
    echo !C_RED![ ERRO ]!C_RESET! SKlauncher.jar não encontrado.
    echo.
    pause
    exit /b 1
)

echo !C_GREEN![  OK  ]!C_RESET! SKlauncher encontrado.

if not exist "%GAME%" (
    echo !C_RED![ ERRO ]!C_RESET! Diretório game não encontrado.
    echo.
    pause
    exit /b 1
)

echo !C_GREEN![  OK  ]!C_RESET! Diretório game encontrado.

for /f "tokens=2 delims==" %%A in ('"%JAVA%" -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%A"

echo !C_GREEN![  OK  ]!C_RESET! Java: !JAVA_VERSION!

if exist "%GAME%\versions\26.2" (
    echo !C_GREEN![  OK  ]!C_RESET! Minecraft 26.2 encontrado localmente.
) else (
    echo !C_YELLOW![ AVISO ]!C_RESET! Minecraft 26.2 não foi encontrado na pasta esperada.
)

if exist "%GAME%\versions\fabric-loader-0.19.4-26.2" (
    echo !C_GREEN![  OK  ]!C_RESET! Fabric encontrado localmente.
) else (
    echo !C_YELLOW![ AVISO ]!C_RESET! Fabric não foi encontrado na pasta esperada.
)

if exist "%GAME%\mods" (
    echo !C_GREEN![  OK  ]!C_RESET! Pasta de mods encontrada.
) else (
    echo !C_YELLOW![ AVISO ]!C_RESET! Pasta de mods não encontrada.
)

echo.
echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo !C_WHITE! STATUS !C_RESET!
echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo.
echo !C_GREEN!  ✓ Ambiente portátil detectado!C_RESET!
echo !C_GREEN!  ✓ Java será executado diretamente da pasta PMC!C_RESET!
echo !C_GREEN!  ✓ Arquivos do jogo serão carregados de game\!C_RESET!
echo !C_YELLOW!  ! O launcher poderá consultar serviços externos quando necessário.!C_RESET!
echo.
echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo !C_WHITE! LOG DO SKLAUNCHER !C_RESET!
echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo.

if exist "%LOG%" (
    echo !C_GRAY!Últimas mensagens do launcher:!C_RESET!
    powershell -NoProfile -Command "$p='%LOG%'; if(Test-Path $p){Get-Content $p -Tail 8}"
    echo.
)

echo !C_BLUE![ PMC ]!C_RESET! Iniciando SKlauncher...
echo !C_GRAY!Java: %JAVA%!C_RESET!
echo !C_GRAY!WorkDir: %GAME%!C_RESET!
echo.

echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo !C_WHITE! SAÍDA DO PROCESSO !C_RESET!
echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo.

"%JAVA%" --add-exports=java.base/jdk.internal.loader=ALL-UNNAMED --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED -jar "%SK%" --workDir "%GAME%" 2>&1

set "EXITCODE=%ERRORLEVEL%"

echo.
echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!

if "%EXITCODE%"=="0" (
    echo !C_GREEN![ PMC ]!C_RESET! SKlauncher foi encerrado normalmente.
) else (
    echo !C_YELLOW![ PMC ]!C_RESET! SKlauncher foi encerrado com código !EXITCODE!.
)

echo !C_CYAN!────────────────────────────────────────────────────────────────────────────────────────────!C_RESET!
echo.
echo !C_GRAY!Log completo:!C_RESET!
echo "%LOG%"
echo.

pause
exit /b %EXITCODE%