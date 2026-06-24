#Requires -Version 5.1
<#
.SYNOPSIS
    Validation System - Desktop Edition: Prerequisites Checker & Installer
.DESCRIPTION
    Verifies all requirements from README and optionally installs missing dependencies.
    Requirements checked:
      1. Java 21 LTS (OpenJDK Temurin)
      2. Maven 3.8+
      3. MySQL 8.0+
      4. Git
      5. Python 3.9+
      6. matplotlib (pip, optional)
      7. FTDI D2XX driver (ftd2xx64.dll)
.NOTES
    Run as Administrator for installation capabilities.
    Usage: .\check_requirements.ps1 [-AutoInstall] [-SkipOptional] [-ReportOnly]
#>

param(
    [switch]$AutoInstall,
    [switch]$SkipOptional,
    [switch]$ReportOnly
)

# ============================================================
# Configuration
# ============================================================
$ErrorActionPreference = "Continue"
$script:TotalChecks = 0
$script:PassedChecks = 0
$script:FailedChecks = 0
$script:WarningChecks = 0
$script:InfoChecks = 0
$script:PendingActions = @()

# ============================================================
# Helper Functions
# ============================================================

function Write-Header {
    param([string]$Title)
    $line = "=" * 60
    Write-Host ""
    Write-Host $line -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host $line -ForegroundColor Cyan
    Write-Host ""
}

function Write-CheckResult {
    param(
        [string]$Name,
        [string]$Status,   # PASS, FAIL, WARN, INFO
        [string]$Detail,
        [string]$Action = ""
    )
    $icon = switch ($Status) {
        "PASS" { $script:TotalChecks++; $script:PassedChecks++; "[OK]" }
        "FAIL" { $script:TotalChecks++; $script:FailedChecks++; "[FAIL]" }
        "WARN" { $script:TotalChecks++; $script:WarningChecks++; "[WARN]" }
        "INFO" { $script:InfoChecks++; "[INFO]" }
    }
    $color = switch ($Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "WARN" { "Yellow" }
        "INFO" { "DarkCyan" }
    }
    Write-Host "  $icon " -ForegroundColor $color -NoNewline
    Write-Host "$Name" -NoNewline
    if ($Detail) { Write-Host " - $Detail" -ForegroundColor Gray } else { Write-Host "" }
    if ($Action) {
        Write-Host "        -> $Action" -ForegroundColor DarkYellow
        $script:PendingActions += [PSCustomObject]@{ Name = $Name; Action = $Action }
    }
}

function Test-CommandExists {
    param([string]$Command)
    try {
        $null = Get-Command $Command -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

function Get-VersionFromOutput {
    param([string]$Output, [string]$Pattern = '(\d+\.\d+[\.\d]*)')
    if ($Output -match $Pattern) { return $Matches[1] }
    return $null
}

function Test-VersionAtLeast {
    param([string]$Current, [string]$Minimum)
    try {
        $cur = [version]$Current
        $min = [version]$Minimum
        return $cur -ge $min
    } catch {
        $curMajor = ($Current -split '[\._]')[0] -as [int]
        $minMajor = ($Minimum -split '[\._]')[0] -as [int]
        if ($curMajor -and $minMajor) { return $curMajor -ge $minMajor }
        return $false
    }
}

function Test-IsAdmin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Test-WingetAvailable {
    return (Test-CommandExists "winget")
}

# Fix #4: Refresh PATH after winget install
function Update-SessionPath {
    $machinePath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
    $userPath = [System.Environment]::GetEnvironmentVariable("PATH", "User")
    $env:PATH = "$machinePath;$userPath"
    Write-Host "        PATH odswiezony w biezacej sesji." -ForegroundColor DarkGray
}

function Install-WithWinget {
    param([string]$PackageId, [string]$Name)
    if ($ReportOnly) { return $false }
    if (-not (Test-WingetAvailable)) {
        Write-Host "        winget nie jest dostepny. Zainstaluj recznie: $Name" -ForegroundColor Red
        return $false
    }
    Write-Host "        Instaluje $Name via winget..." -ForegroundColor Yellow
    try {
        winget install --id $PackageId --accept-source-agreements --accept-package-agreements --silent
        if ($LASTEXITCODE -eq 0) {
            Write-Host "        Zainstalowano $Name pomyslnie!" -ForegroundColor Green
            Update-SessionPath
            Write-Host "        UWAGA: Moze byc konieczny restart terminala dla pelnej widocznosci." -ForegroundColor Yellow
            return $true
        }
    } catch {}
    Write-Host "        Blad instalacji $Name. Sprobuj recznie." -ForegroundColor Red
    return $false
}

function Request-UserConfirmation {
    param([string]$Message)
    if ($ReportOnly) { return $false }
    if ($AutoInstall) { return $true }
    $response = Read-Host "  $Message (T/n)"
    return ($response -eq "" -or $response -match "^[TtYy]")
}

# ============================================================
# Check Functions
# ============================================================

function Test-JavaRequirement {
    Write-Header "1. Java 21 LTS (OpenJDK Temurin)"

    $javaHome = $env:JAVA_HOME
    if ($javaHome) {
        Write-CheckResult "JAVA_HOME" "INFO" $javaHome
    } else {
        Write-CheckResult "JAVA_HOME" "WARN" "Nie ustawiono zmiennej JAVA_HOME"
    }

    $javaVersion = $null

    if (Test-CommandExists "java") {
        $javaVersionOutput = & java -version 2>&1 | Out-String
        $version = Get-VersionFromOutput $javaVersionOutput 'version "(\d+[\.\d]*)'
        if (-not $version) { $version = Get-VersionFromOutput $javaVersionOutput }

        if ($version) {
            $javaVersion = $version
            $majorVersion = ($version -split '\.')[0] -as [int]
            if ($majorVersion -ge 21) {
                Write-CheckResult "Java" "PASS" "Wersja $version (wymagana: 21+)"
            } else {
                Write-CheckResult "Java" "FAIL" "Wersja $majorVersion (wymagana: 21+)" "Zainstaluj Java 21 LTS"
            }
        } else {
            Write-CheckResult "Java" "WARN" "Nie udalo sie odczytac wersji"
        }

        if ($javaVersionOutput -match "Temurin|Eclipse Adoptium") {
            Write-CheckResult "Dystrybucja" "INFO" "Eclipse Temurin (rekomendowana)"
        } else {
            Write-CheckResult "Dystrybucja" "INFO" "Nie jest Temurin - dziala, ale Temurin jest rekomendowany"
        }
    } else {
        Write-CheckResult "Java" "FAIL" "Nie znaleziono polecenia 'java'" "winget install EclipseAdoptium.Temurin.21.JDK"
        if (Request-UserConfirmation "Czy zainstalowac Java 21 (Temurin)?") {
            Install-WithWinget "EclipseAdoptium.Temurin.21.JDK" "Java 21 Temurin"
        }
    }

    # Fix drobnostka: javac version cross-check
    if (Test-CommandExists "javac") {
        $javacOutput = & javac -version 2>&1 | Out-String
        $javacVersion = Get-VersionFromOutput $javacOutput
        if ($javacVersion -and $javaVersion -and $javacVersion -ne $javaVersion) {
            Write-CheckResult "JDK (javac)" "WARN" "javac $javacVersion != java $javaVersion (rozne wersje w PATH!)"
        } else {
            Write-CheckResult "JDK (javac)" "PASS" "Kompilator Java dostepny$(if($javacVersion){" (wersja $javacVersion)"})"
        }
    } else {
        Write-CheckResult "JDK (javac)" "FAIL" "Brak kompilatora - zainstaluj JDK, nie JRE"
    }
}

function Test-MavenRequirement {
    Write-Header "2. Maven 3.8+"

    if (Test-CommandExists "mvn") {
        $mvnOutput = & mvn --version 2>&1 | Out-String
        $version = Get-VersionFromOutput $mvnOutput 'Apache Maven (\d+\.\d+[\.\d]*)'
        if (-not $version) { $version = Get-VersionFromOutput $mvnOutput }

        if ($version) {
            if (Test-VersionAtLeast $version "3.8") {
                Write-CheckResult "Maven" "PASS" "Wersja $version (wymagana: 3.8+)"
            } else {
                Write-CheckResult "Maven" "FAIL" "Wersja $version (wymagana: 3.8+)" "Zaktualizuj Maven"
            }
        } else {
            Write-CheckResult "Maven" "WARN" "Nie udalo sie odczytac wersji"
        }

        if ($mvnOutput -match "Java version: (\d+)") {
            $mavenJava = $Matches[1]
            Write-CheckResult "Maven Java" "INFO" "Maven uzywa Java $mavenJava"
        }
    } else {
        Write-CheckResult "Maven" "FAIL" "Nie znaleziono polecenia 'mvn'" "winget install Apache.Maven"
        if (Request-UserConfirmation "Czy zainstalowac Maven?") {
            Install-WithWinget "Apache.Maven" "Apache Maven"
        }
    }
}

function Test-MySqlRequirement {
    Write-Header "3. MySQL 8.0+"

    $mysqlFound = $false

    if (Test-CommandExists "mysql") {
        $mysqlOutput = & mysql --version 2>&1 | Out-String
        $version = Get-VersionFromOutput $mysqlOutput 'Ver (\d+\.\d+[\.\d]*)'
        if (-not $version) { $version = Get-VersionFromOutput $mysqlOutput }

        if ($version) {
            if (Test-VersionAtLeast $version "8.0") {
                Write-CheckResult "MySQL Client" "PASS" "Wersja $version (wymagana: 8.0+)"
            } else {
                Write-CheckResult "MySQL Client" "FAIL" "Wersja $version (wymagana: 8.0+)"
            }
            $mysqlFound = $true
        }
    }

    $mysqlService = Get-Service -Name "MySQL*" -ErrorAction SilentlyContinue
    if ($mysqlService) {
        foreach ($svc in $mysqlService) {
            if ($svc.Status -eq "Running") {
                Write-CheckResult "MySQL Service" "PASS" "'$($svc.DisplayName)' dziala"
            } else {
                Write-CheckResult "MySQL Service" "WARN" "'$($svc.DisplayName)' status: $($svc.Status)" "Uruchom: Start-Service $($svc.Name)"
            }
        }
        $mysqlFound = $true
    }

    $mysqlPaths = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe"
    )
    foreach ($path in $mysqlPaths) {
        if (Test-Path $path) {
            Write-CheckResult "MySQL binaries" "INFO" "Znaleziono w: $path"
            $mysqlFound = $true
            break
        }
    }

    $mysqldumpPaths = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
    )
    $dumpFound = $false
    if (Test-CommandExists "mysqldump") {
        Write-CheckResult "mysqldump" "PASS" "Dostepny w PATH"
        $dumpFound = $true
    } else {
        foreach ($path in $mysqldumpPaths) {
            if (Test-Path $path) {
                Write-CheckResult "mysqldump" "PASS" "Znaleziono: $path"
                $dumpFound = $true
                break
            }
        }
    }
    if (-not $dumpFound) {
        Write-CheckResult "mysqldump" "WARN" "Nie znaleziono (wymagany dla backupow w .env: MYSQL_DUMP_PATH)"
    }

    if (-not $mysqlFound) {
        Write-CheckResult "MySQL" "FAIL" "Nie znaleziono MySQL" "winget install Oracle.MySQL"
        if (Request-UserConfirmation "Czy zainstalowac MySQL 8.0?") {
            Install-WithWinget "Oracle.MySQL" "MySQL Server 8.0"
        }
    }

    # Fix #3: MySQL — check $LASTEXITCODE and show specific error
    if (Test-CommandExists "mysql") {
        Write-Host ""
        Write-Host "  Sprawdzam baze danych 'validation_desktop_db'..." -ForegroundColor DarkGray
        $dbCheck = & mysql -u root -e "SHOW DATABASES LIKE 'validation_desktop_db';" 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) {
            if ($dbCheck -match "validation_desktop_db") {
                Write-CheckResult "Baza danych" "PASS" "'validation_desktop_db' istnieje"
            } else {
                Write-CheckResult "Baza danych" "WARN" "'validation_desktop_db' nie istnieje" "Utworz baze (patrz README sekcja 'Create Database')"
            }
        } else {
            # Extract MySQL error message for specific diagnosis
            $errorMsg = if ($dbCheck -match "ERROR \d+ \(.+?\): (.+)") { $Matches[1] } 
                        elseif ($dbCheck -match "Access denied") { "Odmowa dostepu (sprawdz haslo root)" }
                        elseif ($dbCheck -match "Can't connect") { "Brak polaczenia z serwerem MySQL (sprawdz czy serwis dziala)" }
                        else { "Blad polaczenia (kod: $LASTEXITCODE)" }
            Write-CheckResult "Baza danych" "INFO" "Nie udalo sie sprawdzic: $errorMsg"
        }
    }
}

function Test-GitRequirement {
    Write-Header "4. Git"

    if (Test-CommandExists "git") {
        $gitOutput = & git --version 2>&1 | Out-String
        $version = Get-VersionFromOutput $gitOutput 'git version (\d+\.\d+[\.\d]*)'
        if (-not $version) { $version = Get-VersionFromOutput $gitOutput }

        if ($version) {
            Write-CheckResult "Git" "PASS" "Wersja $version"
        } else {
            Write-CheckResult "Git" "PASS" "Zainstalowany"
        }
    } else {
        Write-CheckResult "Git" "FAIL" "Nie znaleziono polecenia 'git'" "winget install Git.Git"
        if (Request-UserConfirmation "Czy zainstalowac Git?") {
            Install-WithWinget "Git.Git" "Git"
        }
    }
}

function Test-PythonRequirement {
    Write-Header "5. Python 3.9+ (komunikacja z Testo)"

    $pythonCmd = $null

    foreach ($cmd in @("python", "python3")) {
        if (Test-CommandExists $cmd) {
            $testOutput = & $cmd --version 2>&1 | Out-String
            if ($testOutput -match "Python (\d+\.\d+[\.\d]*)") {
                $pythonCmd = $cmd
                $version = $Matches[1]
                break
            }
        }
    }

    if ($pythonCmd) {
        if (Test-VersionAtLeast $version "3.9") {
            Write-CheckResult "Python" "PASS" "Wersja $version (wymagana: 3.9+) [komenda: $pythonCmd]"
        } else {
            Write-CheckResult "Python" "FAIL" "Wersja $version (wymagana: 3.9+)" "Zaktualizuj Python"
        }

        if (Test-CommandExists "pip") {
            Write-CheckResult "pip" "PASS" "Dostepny"
        } elseif (Test-CommandExists "pip3") {
            Write-CheckResult "pip" "PASS" "Dostepny jako pip3"
        } else {
            Write-CheckResult "pip" "WARN" "Nie znaleziono pip" "python -m ensurepip --upgrade"
        }

        if (-not $SkipOptional) {
            try {
                $matplotCheck = & $pythonCmd -c "import matplotlib; print(matplotlib.__version__)" 2>&1 | Out-String
                if ($matplotCheck -match "(\d+\.\d+)") {
                    Write-CheckResult "matplotlib" "PASS" "Wersja $($Matches[1]) (opcjonalne - wykresy)"
                } else {
                    Write-CheckResult "matplotlib" "WARN" "Nie zainstalowany (opcjonalne)" "pip install matplotlib"
                    if (Request-UserConfirmation "Czy zainstalowac matplotlib (opcjonalne)?") {
                        Write-Host "        Instaluje matplotlib..." -ForegroundColor Yellow
                        & $pythonCmd -m pip install matplotlib 2>&1 | Out-Null
                    }
                }
            } catch {
                Write-CheckResult "matplotlib" "WARN" "Nie zainstalowany (opcjonalne)" "pip install matplotlib"
            }
        }

        $pythonPath = (Get-Command $pythonCmd -ErrorAction SilentlyContinue).Source
        if ($pythonPath -and $pythonPath -match "WindowsApps") {
            Write-CheckResult "Python PATH" "WARN" "Uzywasz Python z Microsoft Store - moze powodowac problemy!" "Zainstaluj z python.org z opcja 'Add to PATH'"
        }
    } else {
        Write-CheckResult "Python" "FAIL" "Nie znaleziono Python" "winget install Python.Python.3.12"
        if (Request-UserConfirmation "Czy zainstalowac Python 3.12?") {
            Install-WithWinget "Python.Python.3.12" "Python 3.12"
        }
    }
}

# Fix #6: FTDI always checked (WARN not FAIL), SkipOptional no longer skips it
function Test-FtdiDriver {
    Write-Header "6. FTDI D2XX Driver (dla Testo 174T)"

    $ftdiPaths = @(
        "C:\Windows\System32\ftd2xx64.dll",
        "C:\Windows\System32\ftd2xx.dll",
        "C:\Windows\SysWOW64\ftd2xx.dll"
    )

    $found = $false
    foreach ($path in $ftdiPaths) {
        if (Test-Path $path) {
            $fileInfo = Get-Item $path
            Write-CheckResult "FTDI Driver" "PASS" "Znaleziono: $path ($([math]::Round($fileInfo.Length/1KB)) KB)"
            $found = $true
        }
    }

    if (-not $found) {
        Write-CheckResult "FTDI Driver" "WARN" "Nie znaleziono ftd2xx64.dll (wymagany tylko dla Testo 174T)" "Pobierz z https://ftdichip.com/drivers/d2xx-drivers/ lub zainstaluj Testo Comfort Software"
    }

    $testoReg = Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*" -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -match "Testo" }
    if ($testoReg) {
        foreach ($app in $testoReg) {
            Write-CheckResult "Testo Software" "INFO" $app.DisplayName
        }
    }
}

function Test-EnvironmentFile {
    Write-Header "7. Konfiguracja projektu"

    # Fix #1: removed dead $projectRoot variable
    $envPaths = @(
        (Join-Path $PSScriptRoot ".env"),
        (Join-Path $PSScriptRoot ".env.example")
    )

    $envFound = $false
    foreach ($path in $envPaths) {
        $filename = Split-Path -Leaf $path
        if (Test-Path $path) {
            Write-CheckResult $filename "PASS" "Znaleziono: $path"
            if ($filename -eq ".env.example" -and -not $envFound) {
                Write-CheckResult ".env" "WARN" "Brak pliku .env" "Skopiuj: cp .env.example .env"
            }
            if ($filename -eq ".env") { $envFound = $true }
        }
    }

    if (-not $envFound -and -not (Test-Path (Join-Path $PSScriptRoot ".env.example"))) {
        Write-CheckResult ".env / .env.example" "INFO" "Nie znaleziono plikow konfiguracyjnych w katalogu skryptu"
    }

    $templatesDir = Join-Path $PSScriptRoot "src\main\resources\templates"
    if (Test-Path $templatesDir) {
        $templates = @("appendix_3_template.docx", "appendix_7_template.docx", "appendix_8_template.docx")
        foreach ($tmpl in $templates) {
            $tmplPath = Join-Path $templatesDir $tmpl
            if (Test-Path $tmplPath) {
                Write-CheckResult "Szablon" "PASS" $tmpl
            } else {
                Write-CheckResult "Szablon" "WARN" "Brak: $tmpl (raporty Word nie beda dzialac)"
            }
        }
    } else {
        Write-CheckResult "Katalog szablonow" "INFO" "Nie znaleziono src/main/resources/templates/ (sprawdz po sklonowaniu repo)"
    }
}

function Test-SystemInfo {
    Write-Header "Informacje o systemie"

    $os = (Get-CimInstance Win32_OperatingSystem)
    Write-CheckResult "System" "INFO" "$($os.Caption) $($os.Version)"

    $arch = $env:PROCESSOR_ARCHITECTURE
    if ($arch -eq "AMD64") {
        Write-CheckResult "Architektura" "PASS" "x64 (wymagana)"
    } else {
        Write-CheckResult "Architektura" "FAIL" "$arch (wymagana: x64)"
    }

    $ram = [math]::Round($os.TotalVisibleMemorySize / 1MB, 1)
    Write-CheckResult "RAM" "INFO" "${ram} GB"

    $disk = Get-PSDrive C
    $freeGB = [math]::Round($disk.Free / 1GB, 1)
    if ($freeGB -ge 5) {
        Write-CheckResult "Dysk C:" "PASS" "${freeGB} GB wolne"
    } else {
        Write-CheckResult "Dysk C:" "WARN" "${freeGB} GB wolne (zalecane min. 5 GB)"
    }

    if (Test-IsAdmin) {
        Write-CheckResult "Uprawnienia" "PASS" "Administrator"
    } else {
        Write-CheckResult "Uprawnienia" "INFO" "Zwykly uzytkownik (instalacja moze wymagac admina)"
    }

    if (Test-WingetAvailable) {
        Write-CheckResult "winget" "PASS" "Dostepny (do automatycznej instalacji)"
    } else {
        Write-CheckResult "winget" "WARN" "Niedostepny (instalacja reczna)"
    }
}

# ============================================================
# Summary
# ============================================================

function Show-Summary {
    $line = "=" * 60
    Write-Host ""
    Write-Host $line -ForegroundColor Cyan
    Write-Host "  PODSUMOWANIE" -ForegroundColor Cyan
    Write-Host $line -ForegroundColor Cyan
    Write-Host ""
    # Fix #2: separate InfoChecks counter, clear accounting
    Write-Host "  Sprawdzone:   $($script:TotalChecks)  (+ $($script:InfoChecks) informacyjnych)" -ForegroundColor White
    Write-Host "  Zaliczone:    $($script:PassedChecks)" -ForegroundColor Green
    Write-Host "  Bledy:        $($script:FailedChecks)" -ForegroundColor Red
    Write-Host "  Ostrzezenia:  $($script:WarningChecks)" -ForegroundColor Yellow
    Write-Host ""

    if ($script:FailedChecks -eq 0) {
        Write-Host "  *** WSZYSTKIE WYMAGANIA SPELNIONE! ***" -ForegroundColor Green
        Write-Host "  Mozesz przejsc do budowania aplikacji:" -ForegroundColor Green
        Write-Host "    mvn clean install" -ForegroundColor White
        Write-Host "    mvn spring-boot:run" -ForegroundColor White
    } else {
        Write-Host "  *** BRAKUJACE WYMAGANIA ($($script:FailedChecks)) ***" -ForegroundColor Red
        Write-Host "  Rozwiaz powyzsze problemy przed uruchomieniem aplikacji." -ForegroundColor Yellow
    }

    if ($script:WarningChecks -gt 0) {
        Write-Host ""
        Write-Host "  Ostrzezenia ($($script:WarningChecks)) nie blokuja uruchomienia," -ForegroundColor Yellow
        Write-Host "  ale moga ograniczac funkcjonalnosc." -ForegroundColor Yellow
    }

    # Fix #1: utilize PendingActions — show collected actions at the end
    if ($script:PendingActions.Count -gt 0) {
        Write-Host ""
        Write-Host "  --------------------------------------------------------" -ForegroundColor DarkGray
        Write-Host "  WYMAGANE AKCJE:" -ForegroundColor White
        foreach ($item in $script:PendingActions) {
            Write-Host "    * $($item.Name): " -ForegroundColor Yellow -NoNewline
            Write-Host $item.Action -ForegroundColor White
        }
    }

    Write-Host ""
    Write-Host $line -ForegroundColor Cyan
}

# ============================================================
# Main Execution
# ============================================================

Clear-Host
Write-Host ""
Write-Host "  ========================================================" -ForegroundColor Magenta
Write-Host "   Validation System - Desktop Edition" -ForegroundColor Magenta
Write-Host "   Weryfikacja wymagan systemowych v1.1" -ForegroundColor Magenta
Write-Host "  ========================================================" -ForegroundColor Magenta
Write-Host "   $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
if ($AutoInstall) { Write-Host "   Tryb: AUTO-INSTALL" -ForegroundColor Yellow }
if ($ReportOnly) { Write-Host "   Tryb: TYLKO RAPORT (bez instalacji)" -ForegroundColor Yellow }
if ($SkipOptional) { Write-Host "   Tryb: Pomijanie opcjonalnych" -ForegroundColor Yellow }
Write-Host ""

# Run checks
Test-SystemInfo
Test-JavaRequirement
Test-MavenRequirement
Test-MySqlRequirement
Test-GitRequirement
Test-PythonRequirement
Test-FtdiDriver
Test-EnvironmentFile

# Show summary
Show-Summary

# Fix #5: Don't block non-interactive use
if ([Environment]::UserInteractive -and -not $ReportOnly) {
    Write-Host "  Nacisnij Enter aby zakonczyc..." -ForegroundColor DarkGray
    Read-Host
}

# Exit code for CI/CD usage
if ($script:FailedChecks -gt 0) { exit 1 } else { exit 0 }
