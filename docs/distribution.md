# Dystrybucja Aplikacji Desktopowej (jpackage)

Dokument opisuje proces generowania natywnego pakietu dystrybucyjnego (obrazu aplikacji lub instalatora MSI/EXE) dla systemu Windows za pomocą narzędzia `jpackage`.

## Wymagania wstępne

Aby zbudować dystrybucyjny pakiet aplikacji, na systemie programisty lub stacji budującej muszą być zainstalowane:
1. **JDK 21** (np. Eclipse Temurin) z poprawnie ustawioną zmienną środowiskową `JAVA_HOME`.
2. **WiX Toolset v3** (wymagany tylko do tworzenia instalatora MSI).
   - Pobierz z: [wixtoolset.org](https://wixtoolset.org/releases/v3-11-2-install/)
   - Upewnij się, że katalog instalacyjny WiX (np. `C:\Program Files (x86)\WiX Toolset v3.11\bin`) został dodany do zmiennej środowiskowej `PATH`.

---

## Budowanie pakietu

W katalogu `scripts/` znajduje się skrypt PowerShell `package.ps1` automatyzujący cały proces.

### 1. Budowanie przenośnego obrazu aplikacji (Portable Application Image)
Domyślny tryb skryptu generuje spakowany runtime Javy z aplikacją, niewymagający instalacji:

```powershell
# Uruchomienie z katalogu głównego projektu
powershell -ExecutionPolicy Bypass -File "scripts/package.ps1"
```

Wynikowy folder znajdziesz w: `target/dist/ValidationSystem/`. Możesz go spakować do formatu ZIP i przekazać użytkownikowi końcowemu. Aplikację uruchamia się plikiem `ValidationSystem.exe`.

### 2. Budowanie instalatora MSI (Windows Installer)
Aby wygenerować instalator instalujący aplikację w systemie Windows:

```powershell
powershell -ExecutionPolicy Bypass -File "scripts/package.ps1" -Type msi
```

Wynikowy instalator `ValidationSystem-1.0.0.msi` zostanie zapisany w katalogu `target/dist/`.

### Opcje skryptu
- `-Type` (opcjonalny, domyślnie `app-image`): typ pakietu wyjściowego (`app-image` lub `msi`).
- `-SkipBuild` (opcjonalny, switch): pomija ponowną kompilację przez Maven i pakuje istniejący plik JAR z katalogu `target/`. Useful przy ponownym pakowaniu bez zmian w kodzie.

---

## Jak to działa?
1. Skrypt sprawdza dostępność `jpackage` i ewentualnie WiX Toolset.
2. Uruchamia `mvn clean package -DskipTests` w celu wygenerowania pliku fat-jar Spring Boot + JavaFX.
3. Wywołuje `jpackage`, przekazując plik JAR oraz parametry instalatora (dodanie skrótu do menu start, pulpit, rejestracja nazwy dostawcy).
4. `jpackage` automatycznie kompiluje zminimalizowany obraz maszyny wirtualnej Java (JVM runtime) za pomocą narzędzia `jlink` i dołącza go do paczki. Użytkownik końcowy nie musi mieć zainstalowanej Javy w systemie.
