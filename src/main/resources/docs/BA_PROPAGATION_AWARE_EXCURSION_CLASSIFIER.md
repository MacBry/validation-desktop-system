# BA-EXC002: Klasyfikacja Ekskursji na Podstawie Wektora Propagacji Ciepła
## Business Analysis Document — Propagation-Aware Excursion Classifier

| Pole | Wartość |
|---|---|
| Identyfikator | BA-EXC002 v1.0 |
| System | `validation-desktop-system` (JavaFX 21 / Spring Boot 3.2) |
| Powiązane dokumenty | BA-DP001 v1.0, IMPL-DP001 v1.0 |
| Nadpisuje | `ExcursionDetector.isFrontPosition()` — hardcoded binarny podział front/back |
| Status | Draft |
| Data | 2026-06-25 |

---

## 1. Kontekst biznesowy

### 1.1 Stan obecny

`ExcursionDetector` (Faza 2, DP-001 §4.4) klasyfikuje szpilki temperaturowe jako DEFROST / DOOR_EVENT / EXCURSION. Klasyfikacja przestrzenna opiera się na jednej funkcji:

```java
private boolean isFrontPosition(GridPosition pos) {
    return pos == GridPosition.TOP_FRONT_LEFT 
        || pos == GridPosition.TOP_FRONT_RIGHT 
        || pos == GridPosition.BOTTOM_FRONT_LEFT 
        || pos == GridPosition.BOTTOM_FRONT_RIGHT;
}
```

Logika decyzyjna:
- Jeśli najwcześniej reagujący czujnik jest na froncie → `DOOR_EVENT`
- Jeśli najwcześniej reagujący czujnik jest na tyle → `DEFROST`

**To założenie jest poprawne wyłącznie dla komór typu reach-in z ewaporatorem zamontowanym na tylnej ścianie i drzwiami z przodu.**

### 1.2 Problem biznesowy

W praktyce laboratoryjnej i farmaceutycznej spotykamy komory o różnej geometrii nawiewu:

| Konfiguracja | Typ urządzenia (przykłady) | Kierunek propagacji defrostu |
|---|---|---|
| Ewaporator na tylnej ścianie | Liebherr MediLine LKPv, Thermo TSX505 | Tył → Przód |
| Nawiew sufitowy (ceiling-mounted) | Pol-Eko CHL 700, Binder KB series | Góra → Dół |
| Ewaporator na prawej ścianie | Niektóre modele Sanyo/Panasonic MDF | Prawo → Lewo |
| Dual evaporator (tył + lewa) | Duże komory walk-in, NorLake Scientific | Dwa wektory jednocześnie |
| Nawiew dolny (under-shelf) | Kirsch BL series, lodówki z półką nawiewową | Dół → Góra |

Obecna implementacja **błędnie klasyfikuje** zdarzenia w każdej konfiguracji innej niż "ewaporator z tyłu":

**Przypadek 1 — Nawiew sufitowy (komory w laboratorium użytkownika):**
Defrost propaguje się z góry na dół. Czujniki TOP_FRONT reagują pierwsze → algorytm klasyfikuje defrost jako DOOR_EVENT. **Fałszywie pozytywne DOOR_EVENT, fałszywie negatywne DEFROST.**

**Przypadek 2 — Ewaporator na prawej ścianie:**
Gradient idzie od prawej do lewej. Algorytm widzi `TOP_BACK_RIGHT` jako "nie-front" → klasyfikuje poprawnie jako DEFROST, ale `TOP_FRONT_RIGHT` (który jest blisko ewaporatora) → klasyfikuje jako DOOR_EVENT. **Mieszane, niespójne wyniki.**

**Przypadek 3 — Dwa ewaporatory:**
Dwa wektory propagacji. Algorytm nie potrafi rozróżnić dwóch źródeł ciepła od jednego rozległego zdarzenia.

### 1.3 Konsekwencja audytowa

Błędna klasyfikacja ma konsekwencje w raporcie walidacyjnym:
- DEFROST raportowany jako DOOR_EVENT → audytor widzi zbyt dużo "otwarć drzwi" (podejrzenie na błędy proceduralne)
- DOOR_EVENT raportowany jako DEFROST → audytor widzi rzekomo normalne defrosty tam, gdzie ktoś otwierał drzwi (zamaskowane naruszenie procedury)
- W trybie QUALIFICATION: EXCURSION w STEADY_STATE → FAIL — błędna klasyfikacja może zarówno fałszywie wykluczyć urządzenie, jak i fałszywie je przepuścić

### 1.4 Cel zmiany

Zastąpić statyczny podział `isFrontPosition()` mechanizmem opartym na:
1. **Algorytmicznym wyznaczeniu wektora propagacji ciepła** z sekwencji czasowej reakcji czujników w siatce 4×4 (8 pozycji GridPosition, 2 warstwy × 4 narożniki)
2. **Konfigurowalnej deklaracji pozycji źródeł nawiewu/ewaporatora** na poziomie encji `CoolingChamber`
3. **Walidacji krzyżowej** — wektor obliczony musi być zgodny z zadeklarowanym źródłem; rozbieżność → obniżony confidence + flaga do weryfikacji operatora

---

## 2. Definicje biznesowe

### 2.1 Słownik pojęć (rozszerzenie BA-DP001)

| Pojęcie | Definicja |
|---|---|
| **Wektor propagacji** | Kierunek rozprzestrzeniania się anomalii termicznej w przestrzeni komory, wyznaczony z kolejności reakcji czujników. Wyrażony jako wektor 3D (dx, dy, dz) w układzie współrzędnych komory. |
| **Źródło nawiewu (AirflowSource)** | Fizyczna lokalizacja ewaporatora, grzałki odszraniającej lub wentylatora nawiewowego. Deklarowana przez operatora w konfiguracji komory. |
| **Strefa bliskiego pola** | Zbiór pozycji GridPosition geometrycznie najbliższych zadeklarowanemu źródłu nawiewu. Odpowiednik obecnego `isFrontPosition()`, ale dynamiczny. |
| **Strefa dalekiego pola** | Zbiór pozycji GridPosition najdalszych od zadeklarowanego źródła nawiewu. Odpowiednik obecnego "not isFrontPosition()". |
| **Opóźnienie reakcji (lag)** | Różnica czasu między momentem, gdy dany czujnik przekroczy próg gradientowy, a momentem reakcji pierwszego czujnika w grupie. Wyrażone w minutach. |
| **Zgodność kierunkowa** | Miara spójności obliczonego wektora propagacji z oczekiwanym kierunkiem na podstawie zadeklarowanego źródła. Wartość 0.0–1.0. |

### 2.2 Układ współrzędnych komory

Siatka 8 pozycji `GridPosition` mapuje się na układ 3D (x, y, z):

```
         Widok z góry (patrząc z góry na komorę):
         
         Tył (BACK)
         ┌──────────────────────┐
         │  TBL          TBR   │   TBL = TOP_BACK_LEFT
         │                      │   TBR = TOP_BACK_RIGHT
         │                      │   TFL = TOP_FRONT_LEFT
         │  TFL          TFR   │   TFR = TOP_FRONT_RIGHT
         └──────────────────────┘
         Przód (FRONT) = drzwi
         
         Widok z boku:
         ┌──────────────────────┐
         │  TOP (góra)          │   Warstwa TOP:  TFL, TFR, TBL, TBR
         │                      │
         │  BOTTOM (dół)        │   Warstwa BOTTOM: BFL, BFR, BBL, BBR
         └──────────────────────┘
```

Współrzędne znormalizowane [0, 1]:

| GridPosition | x (lewo→prawo) | y (przód→tył) | z (dół→góra) |
|---|---|---|---|
| `TOP_FRONT_LEFT` | 0.0 | 0.0 | 1.0 |
| `TOP_FRONT_RIGHT` | 1.0 | 0.0 | 1.0 |
| `TOP_BACK_LEFT` | 0.0 | 1.0 | 1.0 |
| `TOP_BACK_RIGHT` | 1.0 | 1.0 | 1.0 |
| `BOTTOM_FRONT_LEFT` | 0.0 | 0.0 | 0.0 |
| `BOTTOM_FRONT_RIGHT` | 1.0 | 0.0 | 0.0 |
| `BOTTOM_BACK_LEFT` | 0.0 | 1.0 | 0.0 |
| `BOTTOM_BACK_RIGHT` | 1.0 | 1.0 | 0.0 |

### 2.3 Predefiniowane konfiguracje źródeł nawiewu

| AirflowSourcePreset | Opis | Pozycje bliskiego pola |
|---|---|---|
| `REAR_WALL` | Ewaporator na tylnej ścianie (domyślny, obecne zachowanie) | TBL, TBR, BBL, BBR |
| `CEILING` | Nawiew sufitowy | TFL, TFR, TBL, TBR |
| `FLOOR` | Nawiew dolny (pod półką) | BFL, BFR, BBL, BBR |
| `LEFT_WALL` | Ewaporator na lewej ścianie | TFL, TBL, BFL, BBL |
| `RIGHT_WALL` | Ewaporator na prawej ścianie | TFR, TBR, BFR, BBR |
| `REAR_AND_LEFT` | Dual: tył + lewa | TBL, BBL, TBR, BBR, BFL |
| `REAR_AND_CEILING` | Dual: tył + sufit | TBL, TBR, TFL, TFR, BBL, BBR |
| `CUSTOM` | Operator ręcznie wskazuje pozycje źródła | Definiowane per komora |

---

## 3. Wymagania biznesowe

### 3.1 Wymagania funkcjonalne (MoSCoW)

#### MUST HAVE

| ID | Wymaganie | Uzasadnienie |
|---|---|---|
| EXC2-F01 | System musi wyznaczać wektor propagacji ciepła z sekwencji opóźnień reakcji czujników w grupie nakładających się szpilek | Algorytmiczna detekcja kierunku — nie wymaga konfiguracji, działa od razu |
| EXC2-F02 | Encja `CoolingChamber` musi przechowywać deklarację pozycji źródła nawiewu (`AirflowSourcePreset` + opcjonalny `Set<GridPosition>` dla trybu CUSTOM) | Konfigurowalność per komora — różne urządzenia w jednym laboratorium |
| EXC2-F03 | Algorytm klasyfikacji musi porównywać obliczony wektor propagacji z oczekiwanym kierunkiem wynikającym z deklarowanego źródła | Walidacja krzyżowa — algorytm + deklaracja = wyższy confidence |
| EXC2-F04 | Jeśli wektor propagacji jest zgodny z kierunkiem od źródła nawiewu → `DEFROST` (confidence ≥ 0.85) | Poprawna klasyfikacja niezależnie od geometrii komory |
| EXC2-F05 | Jeśli wektor propagacji jest zgodny z kierunkiem od drzwi (front) → `DOOR_EVENT` (confidence ≥ 0.85) | Jak wyżej, ale dla otwarcia drzwi |
| EXC2-F06 | Jeśli wektor propagacji nie jest zgodny ani z kierunkiem źródła, ani drzwi → `EXCURSION` + flaga do weryfikacji operatora | Bezpieczny fallback — system nie zgaduje |
| EXC2-F07 | Domyślna wartość `AirflowSourcePreset` dla istniejących komór = `REAR_WALL` | Kompatybilność wsteczna — obecne komory działają bez zmian |
| EXC2-F08 | Jeśli wektor propagacji i deklaracja są sprzeczne → confidence obniżony do ≤ 0.6 + notatka "Wektor propagacji niezgodny z deklarowanym źródłem" | Sygnał do human-in-the-loop zamiast cichego błędu |

#### SHOULD HAVE

| ID | Wymaganie | Uzasadnienie |
|---|---|---|
| EXC2-F09 | UI JavaFX: ComboBox `AirflowSourcePreset` w dialogu edycji komory (`CoolingChamberDialogController`) | Operator musi mieć gdzie zadeklarować konfigurację |
| EXC2-F10 | UI JavaFX: Dla trybu `CUSTOM` — CheckBox per GridPosition do wskazania pozycji bliskich źródłu | Pełna elastyczność dla niestandardowych geometrii |
| EXC2-F11 | Raport PDF: sekcja regime-aware powinna zawierać obliczony wektor propagacji i deklarowane źródło w notatkach segmentu | Pełna traceability dla audytora |
| EXC2-F12 | Migracja danych: nowa kolumna w `cooling_chambers` z domyślną wartością `REAR_WALL` | Istniejące dane nie wymagają ręcznej aktualizacji |

#### COULD HAVE

| ID | Wymaganie | Uzasadnienie |
|---|---|---|
| EXC2-F13 | Auto-sugestia źródła nawiewu na podstawie analizy historycznych sesji mapowania danej komory | Uczenie się z danych — po kilku sesjach system sam sugeruje konfigurację |
| EXC2-F14 | Wizualizacja wektora propagacji na schemacie 3D komory w UI diagnostycznym | Intuicyjna walidacja wizualna |

#### WON'T HAVE (w tym scope)

| ID | Wymaganie | Powód wykluczenia |
|---|---|---|
| EXC2-W01 | Dynamiczny grid > 8 pozycji | Zmiana GridPosition wymaga refaktoru w wielu komponentach; poza scope |
| EXC2-W02 | Automatyczna kalibracja progów na podstawie modelu CFD | Wymaga integracji z zewnętrznym oprogramowaniem symulacyjnym |

---

## 4. Algorytm wyznaczania wektora propagacji

### 4.1 Dane wejściowe

Algorytm operuje na grupie nakładających się szpilek (jak obecny `groupOverlappingSpikes()`). Dla każdej szpilki znamy:
- `GridPosition` → współrzędne (x, y, z) w przestrzeni komory
- `fromTimestamp` → moment przekroczenia progu gradientowego (początek szpilki)

### 4.2 Wyznaczanie wektora (metoda ważonej regresji opóźnień)

```
Wejście: grupa N szpilek z pozycjami (x_i, y_i, z_i) i czasami startu t_i

1. Wyznacz t_min = min(t_i)  — czas reakcji pierwszego czujnika
2. Oblicz opóźnienie lag_i = t_i - t_min  [minuty] dla każdego czujnika
3. Odrzuć czujniki z lag_i = 0 (reakcja jednoczesna — nie niosą informacji kierunkowej)
4. Jeśli pozostało < 2 czujników z niezerowym lagiem → wynik: INCONCLUSIVE
5. Oblicz centroid pozycji czujników z lag_i = 0:
      C_source = mean(x_i, y_i, z_i) dla lag_i = 0
6. Oblicz centroid pozycji czujników z max(lag_i):
      C_far = mean(x_j, y_j, z_j) dla lag_j = max(lag)
7. Wektor propagacji:
      V = C_far - C_source  (znormalizowany do długości 1)
8. Jeśli ||V|| < 0.1 → wynik: INCONCLUSIVE (brak wyraźnego kierunku)
```

### 4.3 Predefiniowane wektory referencyjne

| AirflowSourcePreset | Oczekiwany wektor propagacji defrostu (dx, dy, dz) | Oczekiwany wektor drzwi (dx, dy, dz) |
|---|---|---|
| `REAR_WALL` | (0, -1, 0) — od tyłu do przodu | (0, +1, 0) — od przodu do tyłu |
| `CEILING` | (0, 0, -1) — z góry na dół | (0, +1, 0) — od przodu do tyłu |
| `FLOOR` | (0, 0, +1) — z dołu do góry | (0, +1, 0) — od przodu do tyłu |
| `LEFT_WALL` | (+1, 0, 0) — od lewej do prawej | (0, +1, 0) — od przodu do tyłu |
| `RIGHT_WALL` | (-1, 0, 0) — od prawej do lewej | (0, +1, 0) — od przodu do tyłu |
| `REAR_AND_LEFT` | (+0.71, -0.71, 0) — ukośnie | (0, +1, 0) — od przodu do tyłu |
| `REAR_AND_CEILING` | (0, -0.71, -0.71) — ukośnie tył+góra→przód+dół | (0, +1, 0) — od przodu do tyłu |

### 4.4 Klasyfikacja na podstawie cosine similarity

```
1. Oblicz cos_defrost = cosine_similarity(V, V_expected_defrost)
2. Oblicz cos_door    = cosine_similarity(V, V_expected_door)
3. Decyzja:
   - cos_defrost ≥ 0.7 i cos_defrost > cos_door  → DEFROST
   - cos_door ≥ 0.7 i cos_door > cos_defrost      → DOOR_EVENT
   - oba < 0.7 lub różnica < 0.1                   → EXCURSION (nierozstrzygnięte)
```

### 4.5 Confidence score

```
base_confidence = max(cos_defrost, cos_door)

Jeśli deklaracja źródła jest zgodna z wektorem:
    final_confidence = min(base_confidence + 0.1, 1.0)
Jeśli deklaracja źródła jest sprzeczna z wektorem:
    final_confidence = max(base_confidence - 0.25, 0.3)
    note += "UWAGA: Wektor propagacji niezgodny z deklarowanym źródłem nawiewu"
Jeśli brak deklaracji (CUSTOM bez pozycji):
    final_confidence = base_confidence  (bez korekty)
```

### 4.6 Interakcja z istniejącą detekcją okresowości

Mechanizm okresowości (sekcja A w `classifySpikes()`) pozostaje **bez zmian** — jeśli szpilki powtarzają się co 4-12h z tolerancją ±30min, zostają oznaczone jako DEFROST niezależnie od wektora propagacji. Wektor propagacji działa jako **dodatkowa warstwa klasyfikacji** dla szpilek, które nie przeszły testu okresowości lub dla których istnieje konflikt między okresowością a sygnaturą przestrzenną.

Priorytet:
1. Okresowość (4-12h, ±30min) → DEFROST (niezmienny)
2. Wektor propagacji + deklaracja źródła → DEFROST / DOOR_EVENT / EXCURSION
3. Zdarzenia izolowane (1 czujnik) → reguła ≤20min/DOOR, >20min/EXCURSION (niezmienna)

---

## 5. Wpływ na istniejące komponenty

### 5.1 Komponenty modyfikowane

| Komponent | Zakres zmiany |
|---|---|
| `CoolingChamber` (encja) | +2 pola: `airflowSourcePreset`, `customAirflowPositions` |
| `ExcursionDetector` | Zastąpienie `isFrontPosition()` przez `PropagationVectorClassifier` |
| `RegimeDetectionProperties` | +3 parametry: `propagation.cosineSimilarityThreshold`, `propagation.ambiguityMargin`, `propagation.defaultPreset` |
| `CoolingChamberDialogController` | +ComboBox AirflowSourcePreset, +CheckBoxy CUSTOM |
| `RegimeAwareSectionRenderer` | Notatka wektora propagacji w segmentach DEFROST/DOOR |

### 5.2 Nowe komponenty

| Komponent | Odpowiedzialność |
|---|---|
| `PropagationVectorClassifier` | Wyznaczenie wektora, cosine similarity, klasyfikacja |
| `AirflowSourcePreset` (enum) | Predefiniowane konfiguracje nawiewu |
| `GridPositionCoordinates` (utility) | Mapowanie GridPosition → (x, y, z) |
| Flyway `V??__AddAirflowSourceToChambres.sql` | Migracja schematu |

### 5.3 Komponenty niezmienione

- `DefrostCycleDetector` — detekcja statystyczna per czujnik, niezależna od geometrii
- `OlsSegmentor`, `CusumDetector` — segmentacja bazowa, ortogonalna
- `StatisticsAggregationService` — agregacja FFT/defrost cykli, bez zmian

---

## 6. Wymagania niefunkcjonalne

| ID | Wymaganie | Kryterium akceptacji |
|---|---|---|
| EXC2-NF01 | Kompatybilność wsteczna | Istniejące komory z domyślnym REAR_WALL produkują identyczne wyniki jak obecna implementacja |
| EXC2-NF02 | Wydajność | Obliczenie wektora propagacji dla grupy ≤8 czujników < 1ms |
| EXC2-NF03 | Audytowalność | Wektor propagacji, cosine similarity i deklaracja źródła zapisane w `MeasurementSegment.note` |
| EXC2-NF04 | Migracja schematu | Flyway migracja addytywna, nie destrukcyjna — nowe kolumny z DEFAULT |
| EXC2-NF05 | Feature flag | Nowa klasyfikacja za `regime.detection.propagationAware=true` (domyślnie `false` — fallback do `isFrontPosition()`) |

---

## 7. Kryteria akceptacji (Definition of Done)

| ID | Kryterium | Metoda weryfikacji |
|---|---|---|
| AC-01 | Komora z nawiewem sufitowym: defrosty klasyfikowane jako DEFROST, nie DOOR_EVENT | Test syntetyczny: szpilka propagująca z góry na dół |
| AC-02 | Komora z ewaporatorem na prawej ścianie: defrosty klasyfikowane poprawnie | Test syntetyczny: szpilka propagująca od prawej do lewej |
| AC-03 | Komora z dual evaporator: defrosty rozpoznane mimo dwóch wektorów | Test syntetyczny: dwie jednoczesne propagacje |
| AC-04 | Istniejące komory (REAR_WALL): wyniki identyczne z obecną implementacją | Test regresyjny na danych referencyjnych sesji 2026-06-21 |
| AC-05 | Sprzeczność wektor/deklaracja → confidence ≤ 0.6 + notatka | Test jednostkowy |
| AC-06 | Grupa z < 2 czujnikami z niezerowym lagiem → INCONCLUSIVE/EXCURSION | Test jednostkowy |
| AC-07 | Migracja Flyway bez utraty danych | Test na kopii bazy produkcyjnej |
| AC-08 | UI: ComboBox AirflowSourcePreset wyświetla i zapisuje wartość | Test manualny JavaFX |

---

## 8. Ryzyka i mitygacje

| Ryzyko | Prawdopodobieństwo | Wpływ | Mitygacja |
|---|---|---|---|
| Interwał logowania zbyt długi (np. 15 min) — lag między czujnikami nierozróżnialny | Wysokie dla rejestratorów z długim interwałem | Wektor INCONCLUSIVE → fallback do deklaracji | Jeśli lag < 1 interwał logowania → poleganie na samej deklaracji zamiast wektora |
| Operator nie zna/nie deklaruje lokalizacji ewaporatora | Średnie | Domyślny REAR_WALL może być błędny | Notatka w UI: "Upewnij się, że deklaracja źródła nawiewu odpowiada fizycznej konfiguracji komory" |
| Komora z wieloma źródłami ciepła (np. grzałki na drzwiach do ochrony uszczelek) | Niskie | Może generować fałszywy kierunek propagacji | CUSTOM mode pozwala operatorowi wskazać dokładne pozycje |
| Niewystarczająca liczba czujników reagujących (np. tylko 2 z 8) | Średnie | Wektor niedokładny | Minimum 3 czujniki z niezerowym lagiem dla confidence > 0.7; poniżej → obniżony confidence |
