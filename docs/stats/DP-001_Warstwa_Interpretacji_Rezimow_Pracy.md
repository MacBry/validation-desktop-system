# Dokument Projektowy: Warstwa Interpretacji Reżimów Pracy

**Regime-Aware Interpretation Layer dla systemu mapowania i walidacji komór termicznych**

| Pole | Wartość |
|---|---|
| Identyfikator dokumentu | DP-001 |
| System | `validation-desktop-system` (JavaFX 21 / Spring Boot 3.2 / MySQL · H2 / Flyway / Hibernate Envers) |
| Wersja | 1.0 (Draft) |
| Status | Do przeglądu |
| Typ | Specyfikacja projektowa / Design Document |
| Data | 2026-06-21 |
| Powiązania | Raport Rewalidacji GxP (przypadek referencyjny: lodówko-zamrażarka Amica, nr inw. 11952750024036) |

---

## 1. Cel dokumentu

Dokument opisuje zidentyfikowane ograniczenie obecnej wersji systemu — brak warstwy interpretacji czasowo-przyczynowej danych pomiarowych — oraz proponowane rozwiązanie w postaci **warstwy świadomej reżimu pracy** (regime-aware interpretation layer). Dokument służy jako podstawa do wdrożenia przyrostowego oraz jako element dokumentacji projektowej repozytorium.

Zakres: model domenowy, algorytmy detekcji, warunkowanie statystyk, werdykt zależny od trybu runu, walidacja samego rozwiązania oraz plan wdrożenia.

## 2. Definicje i skróty

| Skrót | Znaczenie |
|---|---|
| Reżim (segment) | Spójny czasowo odcinek przebiegu o jednorodnym charakterze pracy (np. dochodzenie do stanu ustalonego, defrost, praca ustalona) |
| Stan ustalony | Faza, w której temperatura oscyluje wokół stałej wartości; warunek poprawnej oceny kwalifikacyjnej |
| Qualification run | Przebieg kwalifikacyjny — ocena, czy komora utrzymuje specyfikację w stanie ustalonym |
| Characterization run | Przebieg charakteryzacyjny — obserwacja zachowania w realnym użyciu, z dynamiką jako sygnałem |
| Ekskursja | Przejściowe przekroczenie/odchylenie temperatury (np. szpilka defrostu, otwarcie drzwi) |
| SPC | Statistical Process Control (karty Shewharta, reguły Nelsona, Cp/Cpk) |
| CUSUM | Cumulative Sum — metoda detekcji trwałej zmiany poziomu średniej |
| MKT | Mean Kinetic Temperature |
| CSV | Computerised System Validation |

## 3. Opis problemu

### 3.1. Kontekst

System generuje kompletny, zgodny ze standardami raport GxP: budżet niepewności GUM z korekcją błędu systematycznego, MKT, Cp/Cpk, test normalności Jarque-Bera, karty Shewharta X-bar/S z regułami Nelsona, jednorodność przestrzenną, traceability rejestrator → świadectwo → ważność, sumę kontrolną SHA-256, automatyczne wyznaczenie hotspot/coldspot. Warstwa opisowa, normatywna i przestrzenna działa poprawnie.

### 3.2. Zaobserwowane ograniczenie

Statystyki i werdykt liczone są na **całym przebiegu jako jednym zbiorze**, bez rozróżnienia odmiennych reżimów pracy współwystępujących w jednej sesji. W efekcie metryki kwalifikacyjne mieszają zmienność zamierzoną (dochodzenie do nastawy, tryb szybkiego chłodzenia, otwarcia drzwi) ze zmiennością z winy układu. System nie odróżnia niestabilności **usterkowej** od **zamierzonej**, a wnioski słowne mają charakter szablonowego menu hipotez, nie diagnozy przypiętej do znaczników czasu.

### 3.3. Dowód na danych (przypadek referencyjny)

Sesja ~25 h, próbkowanie 1/min, 16 rejestratorów Testo 174T (po 8 na komorę), lodówko-zamrażarka eksploatowana w warunkach domowych z celowym włączeniem trybu szybkiego chłodzenia (fastcooling).

Rozkład przebiegu zamrażarki na reżimy ujawnia, że alarmujące metryki są zdominowane przez transient i tryb wymuszony, a nie przez niestabilność sprzętu:

| Okno | Komora zamrażarki (śr.) | Tylna ściana chłodziarki | Charakter |
|---|---|---|---|
| 0–4 h | ~−20°C | +1…+2°C | praca normalna przed wymuszeniem |
| ~6–14 h | zjazd −25 → −31°C | **−4…−6,5°C** | fastcooling — obie komory schładzane na maksimum |
| 16–24 h | ~−32°C (ustalone) | +6…+7°C | powrót do normalnego użytkowania |

Metryki zagregowane po całym przebiegu (mylące, jeśli czytane jako kwalifikacja):

| Metryka | Wartość (cały przebieg) | Interpretacja po segmentacji |
|---|---|---|
| Odch. std (zamrażarka) | ~5,0°C (limit WHO ≤1,0°C) | zdominowane przez ~21 h rampy schładzania, nie przez fazę ustaloną |
| Cpk min (zamrażarka / chłodziarka) | 0,47 / −0,19 | liczone na zmieszanych reżimach; w fazie ustalonej znacząco lepsze |
| Naruszenia reguł Nelsona | 5083 (X-bar) | artefakt transientu + dwóch defrostów na danych minutowych (autokorelacja) |
| Tylna ściana chłodziarki −6,5°C | poza dolnym limitem | **finding** trybu fastcooling (ewaporator z tyłu), nie usterka |

Najszybszy zjazd zamrażarki (~20:53) i najzimniejszy punkt tylnej ściany chłodziarki (−6,5°C o 00:43) wypadają w tym samym oknie wymuszonego chłodzenia — zgodnie z hipotezą operatora. System tego powiązania nie wykrył; interpretację przyczynową dostarczył człowiek ręcznie.

### 3.4. Przyczyna źródłowa

Brakuje warstwy interpretacji czasowo-przyczynowej. Konkretnie system:

1. nie segmentuje przebiegu na reżimy i nie liczy statystyk kwalifikacyjnych warunkowo na fazie ustalonej;
2. nie wykrywa i nie etykietuje zdarzeń (defrost, drzwi, zmiana nastawy);
3. nakłada kryteria **kwalifikacyjne** (WHO ≤1°C, Cpk) na przebieg **charakteryzacyjny**, bez rozpoznania trybu runu;
4. nie wiąże cech sygnału z hipotezą przyczynową.

### 3.5. Skutki

Fałszywie alarmujące werdykty (false positive), ryzyko audytowe wynikające z zastosowania niewłaściwych kryteriów, oraz przeniesienie całej interpretacji na operatora.

## 4. Rozwiązanie

### 4.1. Koncepcja

Wprowadzenie warstwy świadomej reżimu pracy, która: (1) **segmentuje** przebieg, (2) **klasyfikuje** segmenty i zdarzenia, (3) **warunkuje** statystyki kwalifikacyjne na fazie ustalonej, (4) wydaje **werdykt zależny od trybu runu**, (5) generuje **hipotezy przyczynowe** przypięte do danych, z (6) **udziałem człowieka** (potwierdzanie wykrytych zdarzeń).

### 4.2. Zasady projektowe

- **Determinizm i wyjaśnialność.** Algorytmy klasyczne (przetwarzanie sygnału + reguły), bez uczenia maszynowego. W systemie GxP algorytm współdecydujący o werdykcie musi być powtarzalny, audytowalny i sam podlegać walidacji.
- **Człowiek w pętli.** Narzędzie proponuje, operator potwierdza/odrzuca; decyzja zapisywana audytowo (Envers).
- **Algorytm jako deliverable CSV.** Detektor segmentów i zdarzeń jest przedmiotem walidacji z udokumentowanymi kryteriami akceptacji.
- **Wdrożenie przyrostowe za flagą funkcyjną.**

### 4.3. Architektura — model domenowy

```java
enum RunMode { QUALIFICATION, CHARACTERIZATION, MONITORING }

enum SegmentType {
    EQUILIBRATION, STEADY_STATE, DEFROST, DOOR_EVENT,
    SETPOINT_CHANGE, EXCURSION, NORMAL_USE
}

enum DetectionSource { ALGORITHM, OPERATOR }   // human-in-the-loop

record Segment(Instant from, Instant to, SegmentType type,
               double confidence, DetectionSource source, String note) {}
```

`MeasurementSession` otrzymuje atrybut `RunMode` (deklarowany przez operatora) oraz listę `Segment`. Adnotacje operatora stanowią osobną encję audytowaną przez Hibernate Envers (kto, co i kiedy oznaczył). Migracje schematu — Flyway.

### 4.4. Algorytmy detekcji

**Segmentacja stanu ustalonego (rolling OLS).** Regresja liniowa na oknie 30–60 min; klasyfikacja jako `STEADY_STATE`, gdy nachylenie i szerokość pasma są poniżej progów przez ≥ N minut, w przeciwnym razie `EQUILIBRATION`.

```java
double slope = ols(window).slopePerMinute();
boolean steady = Math.abs(slope) < EPS && bandWidth(window) < BAND;
```

**Zmiana poziomu — nastawa / fastcooling (CUSUM).** Trwałe przesunięcie średniej wykrywane metodą CUSUM (komponent dostępny z analizy ORS). Sustained shift = `SETPOINT_CHANGE`.

**Detekcja ekskursji (pochodna + powrót).** Szybki gradient z powrotem w czasie T → kandydat na `DEFROST` lub `DOOR_EVENT`. Rozróżnienie przyczyny:
- **sygnatura przestrzenna** — drzwi: reagują najpierw czujniki przednie/górne; defrost: wzorzec od ewaporatora;
- **okresowość** — test FFT/autokorelacji na ciągu szpilek: regularny interwał ⇒ defrost, nieregularny ⇒ drzwi.

### 4.5. Warunkowanie statystyk i werdykt świadomy reżimu

Metryki kwalifikacyjne (Cpk, odch. std, stabilność WHO) liczone **wyłącznie na segmentach `STEADY_STATE`**; statystyki całości raportowane obok jako „przebieg z transientami". Logika werdyktu wyniesiona do polityki zależnej od `RunMode` (wzorzec Strategy):

```java
interface VerdictPolicy { Verdict evaluate(Segment seg, Metric m); }
// QualificationPolicy:    ekskursja w oknie ustalonym -> FAIL
// CharacterizationPolicy: ta sama ekskursja -> FINDING + rekomendacja
```

Gdy segmentacja wykryje, że przebieg nie jest ustalony, a `RunMode == QUALIFICATION`, raport sygnalizuje: „przebieg dynamiczny — kryteria kwalifikacyjne mogą nie mieć zastosowania". Domyka to niespójność „kryteria kwalifikacyjne nałożone na run charakteryzacyjny".

### 4.6. Generator hipotez przyczynowych

Każde wykryte i potwierdzone zdarzenie generuje zdanie sterowane policzonymi cechami (czas, amplituda, czas trwania, czujniki), np.: „20:32 — +14°C w 6 min na G-TP/G-TL (górne), powrót w 40 min — wzorzec zgodny z otwarciem drzwi". Zastępuje to szablonowy akapit hipotez.

## 5. Walidacja rozwiązania (self-CSV)

Detektor podlega walidacji na zestawie syntetycznych przebiegów referencyjnych o znanych zdarzeniach:

| Przypadek testowy | Oczekiwany wynik detektora |
|---|---|
| Rampa nastawy −0,33°C/min, brak szpilek | 1× `EQUILIBRATION` + `STEADY_STATE`, brak ekskursji |
| Defrost co 8 h, 3 cykle | 3× `DEFROST` oznaczone jako okresowe |
| Pojedyncze otwarcie drzwi | 1× `DOOR_EVENT` (nieokresowe, czujniki przednie pierwsze) |
| Przebieg w pełni ustalony | 100% `STEADY_STATE`, Cpk liczony na całości |

Kryteria akceptacji (do uzupełnienia wartościami): czułość/swoistość detekcji zdarzeń, tolerancja znaczników czasu, brak fałszywej klasyfikacji rampy jako niestabilności. Dokumentacja walidacyjna detektora stanowi osobny deliverable.

## 6. Plan wdrożenia

| Faza | Zakres | Dźwignia |
|---|---|---|
| 0 | Model domenowy (`Segment`, `RunMode`, `Annotation`), migracje Flyway, Envers | fundament |
| 1 | Segmentacja stanu ustalonego (OLS) + CUSUM + warunkowanie statystyk | **najwyższa** |
| 2 | Detektor ekskursji + log zdarzeń (zastępuje zrzut naruszeń Nelsona) | wysoka |
| 3 | Tryb runu + `VerdictPolicy` zależny od reżimu | wysoka |
| 4 | UI adnotacji (JavaFX) + potwierdzanie zdarzeń (human-in-the-loop) | średnia |
| 5 | Generator hipotez przyczynowych + klasyfikacja sygnatury przestrzennej | średnia |

Każda faza wdrażana i testowana niezależnie, za flagą funkcyjną. Faza 1 daje realny przeskok wartości jeszcze przed dotknięciem UI.

## 7. Załącznik A — podsumowanie przypadku referencyjnego

Lodówko-zamrażarka Amica (nr inw. 11952750024036), dwie komory mapowane równolegle, eksploatacja domowa z włączonym trybem fastcooling. Chłodziarka (cel 1,8–8,0°C): trwały gradient przód–tył ~8°C, tylna ściana podmrażająca do −6,5°C w oknie wymuszonego chłodzenia, ~30% odczytów poniżej dolnego limitu. Zamrażarka (cel −35…−18°C): stan ustalony ~−32°C z jednorodnością ~1,6°C, lecz dwie ekskursje defrostu (do −6,9°C) i 3,1% odczytów powyżej −18°C. Wniosek: oba zachowania są poprawnie interpretowalne dopiero po rozdzieleniu reżimów — czego dotyczy niniejszy dokument.

---

*Dokument projektowy — wersja robocza do przeglądu. Wartości progowe algorytmów (EPS, BAND, N, progi pochodnej) do ustalenia w fazie strojenia i zapisania jako parametry konfiguracyjne podlegające kontroli zmian.*
