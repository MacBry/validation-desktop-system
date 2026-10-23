# Faza 4 — Pakowanie aplikacji (jpackage + WiX)

> Dokument planistyczny (BA + plan implementacji) dla ostatniej fazy ticketu 7.
> Cel: wyprodukować podpisywalny instalator Windows aplikacji desktop
> `validation-desktop`, z powtarzalnym buildem w CI i weryfikowalnymi checksumami.
>
> Status: **PLANOWANE** (analiza domknięta, implementacja przed nami).
> Data: 2026-07-27.

---

## 1. Cel i zakres

**W zakresie (Faza 4):**
- Natywny instalator Windows (`.msi`) z ikoną, wpisem w menu Start i skrótem.
- Aplikacja zapakowana wraz z **własnym runtime Java** (użytkownik NIE musi mieć
  zainstalowanego JDK/JRE).
- Deterministyczny build w CI na `windows-latest`.
- Generowanie sum kontrolnych **SHA-256** artefaktu.
- Publikacja instalatora + checksumów jako assety GitHub Release.

**Poza zakresem (świadomie odłożone, patrz §11):**
- Cyfrowe podpisywanie instalatora certyfikatem code-signing (Authenticode) —
  wymaga płatnego certyfikatu; bez niego Windows SmartScreen pokaże ostrzeżenie.
- Instalatory Linux (`.deb`/`.rpm`) i macOS (`.pkg`/`.dmg`).
- Mechanizm auto-update.
- Instalacja/konfiguracja serwera bazy MySQL (instalator dostarcza tylko aplikację).

---

## 2. Kontekst techniczny (co determinuje strategię)

Ustalenia z analizy kodu i `pom.xml` — mają bezpośredni wpływ na dobór narzędzi:

| Fakt | Konsekwencja dla pakowania |
|---|---|
| Aplikacja to **Spring Boot fat-jar** (`spring-boot-maven-plugin` repackage); `Main-Class` w manifeście = `org.springframework.boot.loader.launch.JarLauncher`, `Start-Class` = `ValidationDesktopApplication`. | jpackage dostaje **jeden** wykonywalny jar; punktem wejścia jest launcher Boota, nie klasa aplikacji wprost. |
| `main()` woła `Application.launch(JavaFxApplication.class, args)` — JavaFX startuje pierwszy, w środku podnosi kontekst Springa. | Standardowy start przez fat-jar działa; nie trzeba osobnego launchera JavaFX. |
| **Brak `module-info.java`** — cały kod i zależności są na **classpath**, nie na module-path. | **Nie** stosujemy czystego, modularnego `jlink` na aplikacji. Runtime budujemy z modułów JDK, a aplikacja + JavaFX zostają jako jar na classpath. |
| JavaFX 21.0.2 dostarcza **natywy** (.dll) wewnątrz jarów, rozstrzyganych per-platforma przez Mavena. | **Build pakujący MUSI działać na Windows**, żeby fat-jar zawierał natywy Windows. Cross-build z Linuksa da niedziałający pakiet. |
| Wersja projektu = `1.0.0-SNAPSHOT`. | MSI wymaga wersji **czysto numerycznej** (`MAJOR.MINOR.BUILD`). Kwalifikator `-SNAPSHOT` trzeba usunąć przy przekazaniu do `--app-version`. |
| Baza: MySQL (prod) / H2 (standalone runtime). | Konfiguracja połączenia jest zewnętrzna wobec pakietu — patrz §8. |

---

## 3. Strategia pakowania

Wybór: **`jpackage`** (narzędzie z JDK, dostępne od 14, stabilne w 21) + **WiX Toolset**
jako backend MSI. Uzasadnienie odrzucenia alternatyw:

- ❌ *Czysty modularny `jlink` na aplikacji* — niemożliwy bez `module-info.java`;
  Spring Boot + refleksja czynią modularyzację nieopłacalną.
- ❌ *Zewnętrzne narzędzia (install4j, Advanced Installer)* — komercyjne, zbędne przy
  jednym targecie Windows.
- ✅ *`jpackage` + fat-jar na classpath + odchudzony runtime* — natywne, darmowe,
  część JDK 21, deterministyczne.

Dwa warianty runtime — **rekomendacja: zacząć od (A), przejść do (B) po zaliczeniu smoke-testu:**

- **(A) Pełny runtime JDK** — `jpackage` domyślnie wsadza cały runtime bieżącego JDK.
  Prosty, pewny, ~150–250 MB. Dobry na pierwszą działającą wersję.
- **(B) Odchudzony runtime przez `jlink`** — budujemy minimalny obraz z wyselekcjonowanej
  listy modułów JDK (JavaFX zostaje na classpath, NIE w jlink) i podajemy go przez
  `--runtime-image`. Mniejszy pakiet (~90–120 MB). Wymaga dostrojenia listy modułów (§7.2).

---

## 4. Wymagania środowiska buildu

| Składnik | Wersja / uwaga |
|---|---|
| System | **Windows** (lokalnie Win 11; w CI `windows-latest`). Wymóg twardy — natywy + WiX. |
| JDK | **Temurin 21** (to samo co reszta CI). `jpackage` jest w `$JAVA_HOME/bin`. |
| WiX Toolset | **v3.14** (`candle.exe`, `light.exe` na PATH). ⚠️ `jpackage` w JDK 21 współpracuje z **WiX 3.x**, NIE z WiX 4/5 (zmieniony CLI). W CI instalacja przez `choco install wixtoolset` z przypięciem do gałęzi 3.x. |
| Maven | jak w projekcie (fat-jar buduje `spring-boot-maven-plugin`). |
| Ikona | `.ico` (Windows) w `src/main/resources/packaging/app.ico` — do przygotowania. |

---

## 5. Architektura artefaktu

```
validation-desktop-1.0.0.msi
└── (po instalacji) C:\Program Files\Validation Desktop\
    ├── ValidationDesktop.exe        ← launcher wygenerowany przez jpackage
    ├── app\
    │   ├── validation-desktop-1.0.0.jar   ← Spring Boot fat-jar (kod + JavaFX + natywy)
    │   └── ValidationDesktop.cfg    ← wygenerowany opis (main-jar, opcje JVM)
    └── runtime\                     ← wbudowany runtime Java (wariant A lub B)
```

- **Punkt wejścia:** `--main-jar validation-desktop-1.0.0.jar` (bez `--main-class` →
  jpackage bierze `Main-Class` z manifestu = `JarLauncher`). Alternatywnie jawnie
  `--main-class org.springframework.boot.loader.launch.JarLauncher`.
- **Opcje JVM** (przez `--java-options`): np. `-Xmx512m`, `--enable-native-access=ALL-UNNAMED`
  (JavaFX), ewentualne `-Dspring.profiles.active=prod`.

---

## 6. Wersjonowanie

`1.0.0-SNAPSHOT` → do MSI trafia **`1.0.0`**. W CI wyliczamy:

```bash
# obcięcie -SNAPSHOT z wersji projektu
APP_VERSION=$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout | sed 's/-SNAPSHOT//')
```

Uwaga: MSI dopuszcza tylko `MAJOR.MINOR.BUILD` z zakresami (major/minor ≤ 255,
build ≤ 65535). Bez sufiksów literowych.

---

## 7. Kroki implementacji

### 7.1 Build fat-jara (na Windows)
```bash
mvn -B clean package -DskipTests   # testy już przechodzą w głównym CI; tu chcemy tylko artefakt
# wynik: target/validation-desktop-1.0.0-SNAPSHOT.jar (repackaged, executable)
```

### 7.2 (Wariant B) Odchudzony runtime przez jlink
JavaFX zostaje na classpath (w fat-jarze), więc jlink obejmuje **tylko moduły JDK**.
Startowa lista modułów do dostrojenia smoke-testem (Spring Boot + JPA + TLS + JavaFX runtime):

```
java.base, java.desktop, java.sql, java.naming, java.management,
java.instrument, java.scripting, java.xml, java.logging, java.net.http,
java.security.jgss, jdk.crypto.ec, jdk.crypto.cryptoki, jdk.unsupported,
jdk.zipfs
```
```bash
jlink --no-header-files --no-man-pages --strip-debug --compress=2 \
      --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.instrument,java.scripting,java.xml,java.logging,java.net.http,java.security.jgss,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,jdk.zipfs \
      --output target/runtime
```
> `jdk.unsupported` jest istotny — wiele bibliotek (m.in. przez `sun.misc.Unsafe`)
> bez niego wysypie się dopiero w runtime. Listę weryfikujemy uruchomieniem pakietu,
> nie tylko kompilacją.

### 7.3 jpackage → MSI
```bash
jpackage \
  --type msi \
  --name "ValidationDesktop" \
  --app-version "1.0.0" \
  --vendor "MacBry" \
  --description "Validation System - JavaFX Desktop Edition" \
  --input target \
  --main-jar validation-desktop-1.0.0-SNAPSHOT.jar \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --icon src/main/resources/packaging/app.ico \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --java-options "-Xmx512m" \
  --win-menu --win-shortcut --win-dir-chooser \
  --runtime-image target/runtime \       # tylko wariant B; w A pominąć
  --dest target/installer
# wynik: target/installer/ValidationDesktop-1.0.0.msi
```
Pola `--win-menu`/`--win-shortcut` dają wpis w menu Start i skrót; `--win-dir-chooser`
pozwala wybrać katalog instalacji. `--win-upgrade-uuid <GUID>` warto dodać na stałe,
żeby kolejne wersje aktualizowały poprzednią zamiast instalować obok.

### 7.4 Sumy kontrolne SHA-256
```bash
cd target/installer
sha256sum ValidationDesktop-1.0.0.msi > ValidationDesktop-1.0.0.msi.sha256
# (PowerShell: Get-FileHash ... -Algorithm SHA256)
```

### 7.5 Publikacja
Assety do GitHub Release (tag wersji, np. `v1.0.0`):
```bash
gh release create v1.0.0 --title "..." --notes "..." \
  target/installer/ValidationDesktop-1.0.0.msi \
  target/installer/ValidationDesktop-1.0.0.msi.sha256
```

---

## 8. Konfiguracja bazy po instalacji

Instalator dostarcza **wyłącznie aplikację** — nie stawia serwera MySQL.

- **Standalone / demo:** profil z bazą **H2** (już w zależnościach, scope runtime) —
  działa bez zewnętrznej bazy. Kandydat na domyślny profil instalatora.
- **Produkcja (MySQL):** parametry połączenia z zewnętrznego pliku konfiguracyjnego
  czytanego przy starcie (np. `application-prod.properties` w katalogu obok exe albo
  `%APPDATA%\ValidationDesktop\`), a NIE zaszyte w jarze.

> Decyzja do podjęcia w implementacji: który profil jest domyślny w pakiecie i skąd
> dokładnie aplikacja czyta override'y. Wymaga sprawdzenia obecnej obsługi profili
> w `application*.properties`.

---

## 9. Workflow CI (szkic)

Nowy plik `.github/workflows/package-windows.yml`, wyzwalany ręcznie (`workflow_dispatch`)
i na tagach `v*`. NIE w każdym push — pakowanie jest ciężkie.

```yaml
name: Package Windows Installer
on:
  workflow_dispatch:
  push:
    tags: ['v*']
jobs:
  package:
    runs-on: windows-latest
    timeout-minutes: 40
    permissions:
      contents: write   # publikacja assetów release
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
        with: { java-version: '21', distribution: 'temurin', cache: 'maven' }
      - name: Install WiX 3.x
        run: choco install wixtoolset --version=3.14.0 -y
      - name: Build fat-jar
        run: mvn -B clean package -DskipTests
      - name: jlink runtime (wariant B)
        run: jlink --no-header-files --no-man-pages --strip-debug --compress=2 --add-modules ... --output target/runtime
      - name: jpackage MSI
        shell: pwsh
        run: |
          $ver = (mvn -q help:evaluate -Dexpression=project.version -DforceStdout) -replace '-SNAPSHOT',''
          jpackage --type msi --name ValidationDesktop --app-version $ver ...
      - name: SHA-256
        shell: pwsh
        run: Get-FileHash target/installer/*.msi -Algorithm SHA256 | ForEach-Object { "$($_.Hash)  $([IO.Path]::GetFileName($_.Path))" } | Out-File ...sha256
      - name: Upload artifact
        uses: actions/upload-artifact@v7
        with: { name: windows-installer, path: 'target/installer/*' }
      # na tagu v*: dodatkowo gh release upload
```

---

## 10. Ryzyka i pułapki

| Ryzyko | Mitygacja |
|---|---|
| WiX 4/5 na runnerze zamiast 3.x → jpackage nie znajdzie `light/candle`. | Przypiąć `wixtoolset --version=3.14.0`; zweryfikować `candle.exe -?`. |
| Natywy JavaFX z niewłaściwej platformy (build na Linuksie). | Twardo `runs-on: windows-latest`; nigdy nie pakować z Linuksa. |
| Za wąska lista modułów jlink → `ClassNotFoundException`/`NoClassDefFound` dopiero w runtime. | Smoke-test uruchomienia pakietu (start + logowanie + eksport PDF), nie tylko build. Zacząć od wariantu A. |
| `-SNAPSHOT` w `--app-version` → jpackage odrzuca. | Obcinać sufiks (§6). |
| Refleksja Springa/Hibernate pod odchudzonym runtime. | `jdk.unsupported` + test funkcjonalny; w razie problemów rozszerzyć listę modułów. |
| Brak podpisu → SmartScreen „Nieznany wydawca". | Poza zakresem v1 (§11); udokumentować dla użytkownika. |
| Rozmiar pakietu. | Wariant B + `--strip-debug --compress=2`. |

---

## 11. Poza zakresem / kolejne kroki

1. **Code signing (Authenticode)** — certyfikat OV/EV, `--win-*` + `signtool`. Usuwa
   ostrzeżenie SmartScreen. Wymaga zakupu certyfikatu i bezpiecznego trzymania klucza (CI secret / HSM).
2. **Wieloplatformowość** — Linux `.deb`/`.rpm`, macOS `.dmg` (osobne runnery, macOS wymaga notarization).
3. **Auto-update** — np. przez zewnętrzny mechanizm (poza jpackage).
4. **Zewnętrzny plik konfiguracyjny bazy** — sformalizować lokalizację i format override'ów (§8).

---

## 12. Definition of Done

- [ ] `.github/workflows/package-windows.yml` produkuje `ValidationDesktop-<ver>.msi` na `windows-latest`.
- [ ] Instalator instaluje aplikację z wbudowanym runtime (bez wymogu JDK u użytkownika).
- [ ] Zainstalowana aplikacja **startuje**, loguje i wykonuje eksport PDF (smoke-test).
- [ ] Generowany i publikowany plik `.sha256`.
- [ ] Wpis w menu Start + skrót; kolejne wersje aktualizują (stały `--win-upgrade-uuid`).
- [ ] Ikona `.ico` osadzona.
- [ ] Dokumentacja użytkownika: jak zainstalować i (jeśli MySQL) skąd bierze konfigurację.
```