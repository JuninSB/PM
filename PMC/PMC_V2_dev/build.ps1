$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = if (Test-Path (Join-Path $root 'game')) { $root } else { Join-Path (Split-Path -Parent $root) 'PMC_V2' }
$jdk = Join-Path $root 'tools\jdk'
if (-not (Test-Path $jdk)) { $jdk = Join-Path (Split-Path $project -Parent) 'PMC_V2_build-cache\jdk' }
$javac = Join-Path $jdk 'bin\javac.exe'
$javafx = Join-Path $project 'game\sklauncher\javafx'
$src = Join-Path $root 'src'
$classes = Join-Path $root 'build\classes'
$jar = Join-Path $project 'main\PMC-Launcher.jar'
$manifest = Join-Path $root 'build\manifest.mf'

if (-not (Test-Path $javac)) { throw "JDK de desenvolvimento não encontrado em tools\jdk. Execute tools\download-build-tools.ps1 primeiro." }
$fxJars = (Get-ChildItem $javafx -Filter 'javafx-*.jar' | ForEach-Object FullName) -join [IO.Path]::PathSeparator
if (-not $fxJars) { throw 'Módulos JavaFX locais não encontrados em game\sklauncher\javafx.' }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $jar) | Out-Null
Remove-Item (Join-Path $classes '*') -Recurse -Force -ErrorAction SilentlyContinue
& $javac --release 21 -encoding UTF-8 -cp $fxJars -d $classes (Get-ChildItem $src -Filter *.java -Recurse | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw 'Compilação falhou.' }
@(
    'Manifest-Version: 1.0'
    'Main-Class: pmc.launcher.PmcLauncher'
    'Class-Path: game/sklauncher/javafx/javafx-base-22.0.2-win.jar game/sklauncher/javafx/javafx-controls-22.0.2-win.jar game/sklauncher/javafx/javafx-graphics-22.0.2-win.jar game/sklauncher/javafx/javafx-media-22.0.2-win.jar game/sklauncher/javafx/javafx-swing-22.0.2-win.jar game/sklauncher/javafx/javafx-web-22.0.2-win.jar'
    ''
) | Set-Content -LiteralPath $manifest -Encoding ascii
& (Join-Path $jdk 'bin\jar.exe') --create --file $jar --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Criação do JAR falhou.' }
Write-Host "Criado: $jar"
