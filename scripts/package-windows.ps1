param(
    [string]$PackageType = "app-image"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Resolve-Path (Join-Path $ScriptDir "..")
$PackageRoot = Join-Path $ProjectDir "build\jpackage\windows"
$InputDir = Join-Path $PackageRoot "input"
$ContentDir = Join-Path $PackageRoot "content"
$OutputDir = Join-Path $ProjectDir "packages\windows"
$AppName = "qfolder"
$AppVersion = "1.0.0"

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME no esta configurado. Use JDK 21+ en Windows."
}

$JPackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $JPackage)) {
    throw "jpackage.exe no encontrado en JAVA_HOME. Use JDK 21+."
}

$Mvn = "mvn"
if ($env:MAVEN_HOME) {
    $Mvn = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
}

Write-Host "=== Build app ==="
Push-Location (Join-Path $ProjectDir "p2p-client")
& $Mvn clean package -DskipTests -q
Pop-Location

if (Test-Path $PackageRoot) { Remove-Item $PackageRoot -Recurse -Force }
if (Test-Path $OutputDir) { Remove-Item $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Force $InputDir | Out-Null
New-Item -ItemType Directory -Force (Join-Path $ContentDir "bin") | Out-Null
New-Item -ItemType Directory -Force $OutputDir | Out-Null

Copy-Item (Join-Path $ProjectDir "p2p-client\target\p2p-client-1.0-SNAPSHOT-fat.jar") (Join-Path $InputDir "qfolder.jar")
Copy-Item (Join-Path $ProjectDir "p2p-client\src\main\resources\qfolder.properties.example") (Join-Path $ContentDir "qfolder.properties")

$CloudflaredTarget = Join-Path $ContentDir "bin\cloudflared.exe"
$CloudflaredLocal = Join-Path $env:LOCALAPPDATA "qfolder\bin\cloudflared.exe"
if (Test-Path $CloudflaredLocal) {
    Copy-Item $CloudflaredLocal $CloudflaredTarget
} else {
    Write-Host "=== Download cloudflared windows-amd64 ==="
    Invoke-WebRequest `
        -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" `
        -OutFile $CloudflaredTarget
}

Write-Host "=== jpackage ($PackageType) ==="
$JPackageArgs = @(
    "--type", $PackageType,
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", "qfolder",
    "--description", "qfolder peer-to-peer workspace client",
    "--input", $InputDir,
    "--main-jar", "qfolder.jar",
    "--main-class", "org.q3s.p2p.client.Main",
    "--dest", $OutputDir,
    "--app-content", $ContentDir,
    "--java-options", "-Dqfolder.packaged=true"
)

if ($PackageType -ne "app-image") {
    $JPackageArgs += @("--win-shortcut", "--win-menu")
}

& $JPackage @JPackageArgs

if ($PackageType -eq "app-image") {
    $ZipPath = Join-Path $OutputDir "qfolder-windows-x64.zip"
    if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
    Compress-Archive -Path (Join-Path $OutputDir $AppName) -DestinationPath $ZipPath
}

Write-Host "=== Package listo ==="
Get-ChildItem $OutputDir
