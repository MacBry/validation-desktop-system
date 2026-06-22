# BA-DP001: Warstwa Interpretacji Reżimów Pracy
## Business Analysis Document — Regime-Aware Interpretation Layer

| Pole | Wartość |
|---|---|
| Identyfikator | BA-DP001 v1.0 |
| System | `validation-desktop-system` |
| Powiązany dokument projektowy | DP-001 v1.0 (Draft) |
| Przypadek referencyjny | Amica nr inw. 11952750024036 — sesja 2026-06-21 |
| Status | Do przeglądu |
| Data | 2026-06-21 |

---

## 1. Kontekst biznesowy

### 1.1 Cel systemu

System `validation-desktop` generuje raporty GxP (mapowanie, rewalidacja, kwalifikacja) dla urządzeń chłodniczych używanych w placówkach farmaceutycznych, bankach krwi i laboratoriach. Raporty stanowią dokumentację walidacyjną wymaganą przez regulatorów (GMP Annex 15, PDA TR-64, WHO TRS 961).

### 1.2 Problem biznesowy

Obecna architektura oblicza metryki kwalifikacyjne (Cpk, std dev, MKT, jednorodność przestrzenna, karty Shewharta) na **całym przebiegu pomiarowym jako jednym zbiorze statystycznym**, niezależnie od charakteru danych. W rzeczywistości typowy 24–48h przebieg zawiera kilka odmiennych **reżimów pracy** urządzenia:

```
Typowy 25h przebieg lodówko-zamrażarki (dane rzeczywiste — 2026-06-21):
────────┬─────────────────────────────────────────────────────
0–4h    │ PRACA NORMALNA (stan ustalony)
4–6h    │ EQUILIBRATION (powrót po otwarciu/załadunku)
6–14h   │ FASTCOOLING (wymuszony tryb szybkiego chłodzenia)  ← dominuje statystykę
14–16h  │ EQUILIBRATION (powrót do nastawy)
16–25h  │ STEADY STATE (właściwy stan operacyjny)
────────┴─────────────────────────────────────────────────────
```

Metryki liczone na całym przebiegu **mieszają zmienność zamierzoną** (rampy, ekskursje wymuszane przez operatora) ze **zmiennością z winy układu** (niestabilność regulacji, awaria). System nie potrafi odróżnić tych dwóch źródeł zmienności.

### 1.3 Dowód na danych (przypadek referencyjny)

Sesja 2026-06-21, lodówko-zamrażarka Amica, 16 × Testo 174T, ~25h, próbkowanie 1/min:

| Metryka | Wartość (cały przebieg) | Wartość (STEADY_STATE ~16–25h) | Status werdyktu |
|---|---|---|---|
| Std dev chłodziarki G-TL | 4,694°C | ~0,2–0,4°C (est.) | FALSE POSITIVE |
| T min chłodziarki G-TL | −6,5°C | ~1,5–2,5°C (est.) | FALSE POSITIVE |
| Cpk min chłodziarki | −0,19 | >1,0 (est.) | FALSE POSITIVE |
| Jednorodność chłodziarki (max) | 11,20°C | ~1,5°C (est.) | FALSE POSITIVE |
| Nelson violations X-bar | 4790 | ~20–50 (est.) | FALSE POSITIVE |
| Cpk min zamrażarki | 0,47 | ~0,9–1,2 (est.) | Wymaga weryfikacji |

> [!CAUTION]
> Fałszywie alarmujące werdykty FAIL stanowią ryzyko audytowe: inspektor GMP może zakwestionować urządzenie na podstawie raportu, który nie rozróżnia normalnej eksploatacji od usterki.

---

## 2. Definicje biznesowe

### 2.1 Słownik pojęć

| Pojęcie | Definicja biznesowa |
|---|---|
| **Reżim** | Spójny czasowo odcinek przebiegu o jednolitym charakterze zachowania urządzenia (np. grzanie się po załadunku, praca ustalona, defrost). Granica między reżimami jest wykrywalna algorytmicznie lub deklarowana przez operatora. |
| **Stan ustalony (STEADY_STATE)** | Faza, w której temperatura oscyluje wokół stałej wartości — jedyna faza prawidłowa dla oceny kwalifikacyjnej według norm GMP. |
| **Qualification Run** | Procedura kwalifikacyjna — urządzenie oceniane według rygorystycznych kryteriów WHO/GMP w stanie ustalonym. Wynik: PASS/FAIL z dokumentacją. |
| **Characterization Run** | Procedura charakteryzacyjna — obserwacja zachowania urządzenia w warunkach realnej eksploatacji (z dynamiką jako informacją). Ekskursje są `FINDING`, nie `FAIL`. |
| **Monitoring Run** | Rutynowy nadzór operacyjny — detekcja odchyleń od baseline bez pełnej kwalifikacji. |
| **Ekskursja** | Przejściowe przekroczenie/odchylenie temperatury niemieszczące się w cyklu regularnym. Może być zamierzone (otwarcie drzwi) lub alarmowe (awaria). |
| **CUSUM** | Cumulative Sum — algorytm detekcji trwałej zmiany poziomu średniej (zmiana nastawy, fastcooling). |
| **FastCooling** | Tryb pracy niektórych urządzeń chłodniczych, w którym kompresor pracuje z maksymalną mocą chłodzenia. Powoduje przejściowe przekroczenie dolnego limitu temperatury. |
| **Human-in-the-loop** | Zasada projektowa: system proponuje interpretację, operator zatwierdza/odrzuca. Decyzja zapisana audytowo. |

### 2.2 Typy reżimów (SegmentType)

| Reżim | Kryterium | Metryki kwalifikacyjne? | Typowy czas trwania |
|---|---|---|---|
| `EQUILIBRATION` | Monotoniczny trend, nachylenie >EPS | ❌ Nie | 1–8h |
| `STEADY_STATE` | Brak trendu, wąskie pasmo (≤BAND) przez ≥N min | ✅ Tak | >1h |
| `DEFROST` | Szybki gradient + powrót, sygnatura od ewaporatora, periodyczny | ❌ FINDING | 10–40 min |
| `DOOR_EVENT` | Szybki gradient + powrót, czujniki przednie pierwsze, nieperiodyczny | ❌ FINDING | 3–15 min |
| `SETPOINT_CHANGE` | Trwałe przesunięcie poziomu średniej (CUSUM), brak powrotu | ❌ Segment przejściowy | 2–6h |
| `EXCURSION` | Nagłe odchylenie niepasujące do defrostu/drzwi | ⚠️ Wymaga analizy | Dowolny |
| `NORMAL_USE` | Eksploatacja domowa (wiele małych zdarzeń) | ❌ Nie (Characterization) | Cały przebieg |

### 2.3 Tryby runu (RunMode)

| RunMode | Użycie | Polityka werdyktu |
|---|---|---|
| `QUALIFICATION` | Formalna kwalifikacja IQ/OQ/PQ — rygorystyczna | Ekskursja w STEADY_STATE → FAIL |
| `CHARACTERIZATION` | Poznanie urządzenia, baseline, nowe urządzenie | Ekskursja → FINDING + rekomendacja |
| `MONITORING` | Rutynowy nadzór | Alert przy odchyleniu od baseline |

---

## 3. Wymagania biznesowe

### 3.1 Wymagania funkcjonalne (MoSCoW)

#### MUST HAVE (Faza 1)

| ID | Wymaganie | Uzasadnienie |
|---|---|---|
| BR-01 | System MUSI segmentować przebieg na reżimy przed obliczeniem statystyk | Bez segmentacji metryki są bezużyteczne dla przebiegu z transientem |
| BR-02 | Metryki kwalifikacyjne (Cpk, std dev, MKT) MUSZĄ być obliczane wyłącznie na segmentach `STEADY_STATE` | Wymóg regulatorów: kwalifikacja w stanie ustalonym |
| BR-03 | Raport MUSI prezentować statystyki warunkowe (steady) obok statystyk całego przebiegu | Pełna transparentność — audytor widzi oba zestawy |
| BR-04 | System MUSI deklarować `RunMode` przed wygenerowaniem raportu | Polityka werdyktu zależy od trybu |
| BR-05 | System MUSI alarmować gdy `RunMode == QUALIFICATION` a przebieg nie spełnia kryterium stanu ustalonego | Ochrona przed błędnym werdyktem |

#### SHOULD HAVE (Faza 2–3)

| ID | Wymaganie | Uzasadnienie |
|---|---|---|
| BR-06 | System POWINIEN automatycznie wykrywać i etykietować zdarzenia DEFROST i DOOR_EVENT | Zastępuje szablonowy akapit naruszeń Nelsona |
| BR-07 | System POWINIEN generować zdania hipotezy przyczynowej dla każdego wykrytego zdarzenia | Zmniejsza przeniesienie interpretacji na operatora |
| BR-08 | Operator POWINIEN móc potwierdzać lub odrzucać wykryte zdarzenia (human-in-the-loop) | Wymóg audytowalności |

#### COULD HAVE (Faza 4–5)

| ID | Wymaganie |
|---|---|
| BR-09 | Klasyfikacja sygnatury przestrzennej (drzwi: przód pierwszy; defrost: od ewaporatora) |
| BR-10 | Eksport adnotacji operatora do raportu PDF z podpisem elektronicznym |

#### WON'T HAVE (v1.0)

- Uczenie maszynowe do detekcji reżimów (wymóg determinizmu w GxP — DP-001 §4.2)
- Automatyczna predykcja awarii (inna domena)

### 3.2 Wymagania niefunkcjonalne

| ID | Wymaganie | Miara |
|---|---|---|
| NFR-01 | Determinizm | Ten sam przebieg zawsze daje tę samą segmentację (zero losowości) |
| NFR-02 | Czas segmentacji | <2s dla przebiegu 25h × 8 rejestratorów × 1/min (≤12000 punktów) |
| NFR-03 | Audytowalność | Wszystkie adnotacje operatora zapisane przez Hibernate Envers (kto, kiedy, co) |
| NFR-04 | Wdrożenie za flagą | Feature flag `regime.detection.enabled` — domyślnie `false` do czasu walidacji CSV detektora |
| NFR-05 | Walidacja samego algorytmu | Detektor przechodzi własny pakiet testów CSV z udokumentowanymi kryteriami akceptacji |

---

## 4. Model procesowy

### 4.1 Obecny przepływ (AS-IS)

```
Import danych → Obliczenie statystyk (cały przebieg) → Generowanie PDF
                     ↓
            [Statystyki zmieszanych reżimów]
                     ↓
          [Werdykt na błędnym rozkładzie]
```

### 4.2 Docelowy przepływ (TO-BE)

```
Import danych → Deklaracja RunMode (operator) → Segmentacja (algorytm)
                                                         ↓
                                            Weryfikacja człowiek-w-pętli
                                            (akceptacja/odrzucenie segmentów)
                                                         ↓
                              Obliczenie statystyk warunkowych (STEADY_STATE only)
                              Obliczenie statystyk całego przebiegu (informacyjnie)
                                                         ↓
                              Polityka werdyktu (zależna od RunMode)
                                                         ↓
                              Generowanie PDF (dwa zestawy statystyk + log zdarzeń)
```

---

## 5. Reguły biznesowe werdyktu

### 5.1 QUALIFICATION mode

```
IF segment.type == STEADY_STATE AND temperature > USL → FAIL (naruszenie specyfikacji)
IF segment.type == STEADY_STATE AND temperature < LSL → FAIL (naruszenie specyfikacji)
IF segment.type == STEADY_STATE AND cpk < 1.0        → WARNING (niska zdolność)
IF brak segmentu STEADY_STATE w przebiegu            → WARNING: "przebieg dynamiczny —
                                                        kryteria kwalifikacyjne nie mają zastosowania"
IF EXCURSION w STEADY_STATE                           → FAIL + finding z hipotezą przyczynową
```

### 5.2 CHARACTERIZATION mode

```
IF DEFROST detected    → FINDING: "Wykryto cykl rozmrażania — normalny cykl urządzenia"
IF DOOR_EVENT detected → FINDING: "Wykryto otwarcie drzwi — zdarzenie eksploatacyjne"
IF SETPOINT_CHANGE     → FINDING: "Zmiana nastawy / tryb wymuszony (fastcooling)"
IF EXCURSION           → FINDING + rekomendacja: "Zbadać przyczynę"
STATYSTYKI KWALIFIKACYJNE: raportowane tylko dla STEADY_STATE z oznaczeniem warunkowym
```

### 5.3 Gradacja werdyktu

```
PASS         → wszystkie metryki STEADY_STATE w granicach, bez ekskursji alarmowych
WARNING      → metryki pogranicza lub brak STEADY_STATE
FINDING      → zdarzenie z hipotezą — nie blokuje PASS jeśli STEADY_STATE OK
FAIL         → naruszenie specyfikacji w STEADY_STATE lub brak danych kwalifikacyjnych
INCONCLUSIVE → przebieg zbyt krótki lub zbyt niestabilny do kwalifikacji
```

---

## 6. Interesariusze i wpływ

| Rola | Wpływ zmiany | Działanie |
|---|---|---|
| **Operator systemu** | Nowy krok: deklaracja RunMode + potwierdzenie segmentów | Szkolenie + instrukcja obsługi |
| **Audytor GxP** | Widzi dwa zestawy statystyk + log zdarzeń zamiast szablonowego tekstu | Pozytywny — pełniejsza transparentność |
| **Kierownik walidacji** | Werdykt warunkowy może zmieniać status kwalifikacji urządzenia | Wymaga aktualizacji SOP |
| **Inspektor regulatorowy** | Algorytm detektora musi być walidowany (self-CSV per DP-001 §5) | Deliverable: dokumentacja CSV detektora |

---

## 7. Kryteria akceptacji biznesowej

| Kryterium | Sposób weryfikacji |
|---|---|
| Segmentacja przebiegu referencyjnego (2026-06-21) rozróżnia fastcooling od steady state | Test manualny + wynik segmentacji vs oczekiwany |
| Cpk w fazie STEADY_STATE ≠ Cpk na całym przebiegu | Porównanie wartości liczbowych |
| Raport PDF prezentuje oba zestawy statystyk | Inspekcja wizualna raportu |
| Adnotacja operatora zapisana w Envers z timestampem | Zapytanie do tabeli audytowej |
| Detektor przechodzi 4 przypadki testowe z TP §5 | Zautomatyzowany pakiet testów |
| Czas segmentacji <2s dla 12000 punktów | Test wydajnościowy |
