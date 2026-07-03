# Skrypt budujący natywny pakiet instalacyjny dla Windows za pomocą jpackage
# Wymaga JDK 21 oraz WiX Toolset v3 w celu wygenerowania instalatora MSI.

param (
    [Parameter(Mandatory=$false)]
    [string]$Type = "app-image", # Opcje: app-image, msi
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Rozpoczynanie procesu pakowania Validation System Desktop " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Sprawdzenie JDK i jpackage
$javaVersion = java -version 2>&1 | Out-String
if ($javaVersion -notmatch "21") {
    Write-Warning "Wykryto wersję Javy inną niż JDK 21. jpackage może nie działać prawidłowo."
}

$jpackagePath = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackagePath) {
    # Próba znalezienia jpackage w JAVA_HOME
    if ($env:JAVA_HOME) {
        $jpackagePath = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    }
}

if (-not (Test-Path $jpackagePath)) {
    Write-Error "Nie odnaleziono narzędzia jpackage w systemie! Upewnij się, że masz zainstalowane JDK 21 i ustawioną zmienną JAVA_HOME."
}
Write-Host "Narzędzie jpackage: $jpackagePath" -ForegroundColor Green

# Sprawdzenie WiX Toolset dla instalatora MSI
if ($Type -eq "msi") {
    $wixPath = Get-Command candle -ErrorAction SilentlyContinue
    if (-not $wixPath) {
        Write-Warning "Nie odnaleziono WiX Toolset w PATH. Instalator MSI może się nie zbudować. WiX Toolset jest wymagany dla typu msi."
    } else {
        Write-Host "Wykryto WiX Toolset w PATH." -ForegroundColor Green
    }
}

# 2. Kompilacja projektu
if (-not $SkipBuild) {
    Write-Host "Kompilowanie aplikacji za pomocą Maven..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Błąd kompilacji projektu przez Maven."
    }
    Write-Host "Kompilacja zakończona sukcesem." -ForegroundColor Green
}

# 3. Definicja stałych
$AppName = "ValidationSystem"
$MainJar = "validation-desktop-1.0.0-SNAPSHOT.jar"
$MainClass = "com.mac.bry.desktop.ValidationDesktopApplication"
$InputDir = "target"
$OutputDir = "target\dist"

if (Test-Path $OutputDir) {
    Remove-Item -Recurse -Force $OutputDir
}
New-Item -ItemType Directory -Path $OutputDir | Out-Null

# 4. Uruchomienie jpackage
Write-Host "Budowanie pakietu typu: $Type..." -ForegroundColor Yellow

$jpackageArgs = @(
    "--type", $Type,
    "--dest", $OutputDir,
    "--name", $AppName,
    "--input", $InputDir,
    "--main-jar", $MainJar,
    "--main-class", $MainClass,
    "--win-dir-chooser",
    "--win-shortcut",
    "--win-menu",
    "--vendor", "VCC Pharma",
    "--app-version", "1.0.0"
)

# Jeśli istnieje ikona aplikacji, można ją dołączyć, np. --icon src/main/resources/ui/logo.ico

& $jpackagePath @jpackageArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " Sukces! Pakiet dystrybucyjny został wygenerowany w:      " -ForegroundColor Green
    Write-Host " $(Resolve-Path $OutputDir)                               " -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Cyan
} else {
    Write-Error "Błąd generowania pakietu przez jpackage."
}
