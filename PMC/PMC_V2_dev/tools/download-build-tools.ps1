$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$tools = Join-Path $root 'tools'
$zip = Join-Path $tools 'jdk21.zip'
$jdk = Join-Path $tools 'jdk'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$url = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
Invoke-WebRequest -Uri $url -OutFile $zip
$temp = Join-Path $tools 'jdk-extract'
Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive -LiteralPath $zip -DestinationPath $temp -Force
$folder = Get-ChildItem $temp -Directory | Select-Object -First 1
Remove-Item $jdk -Recurse -Force -ErrorAction SilentlyContinue
Move-Item $folder.FullName $jdk
Remove-Item $temp -Recurse -Force
Remove-Item $zip -Force
Write-Host "JDK de compilação pronto em $jdk"
