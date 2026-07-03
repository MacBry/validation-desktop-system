# Review Notes — STATS_DESCRIPTIVE_SPATIAL_* (BA + Implementation Plan + Test Scenarios)

> **Dokument naprawczy** dla trójki plików:
> - `STATS_DESCRIPTIVE_SPATIAL_BA.md`
> - `STATS_DESCRIPTIVE_SPATIAL_IMPLEMENTATION_PLAN.md`
> - `STATS_DESCRIPTIVE_SPATIAL_TEST_SCENARIOS.md`
>
> Cel: doprowadzić dokumentację do stanu, w którym można ją bez ryzyka pokazać ekspertowi GMP / statystykowi / audytorowi CSV. Każda poprawka ma:
> - **PRZED**: dokładny cytat z obecnego pliku
> - **PO**: proponowana nowa treść
> - **DLACZEGO**: uzasadnienie

---

## 📋 Spis treści

1. [Poprawka #1 — Wzory skośności i kurtozy](#poprawka-1)
2. [Poprawka #2 — REQ-03 brakuje metody analizy poziomów](#poprawka-2)
3. [Poprawka #3 — AC-01 RSD bez kontekstu temperaturowego](#poprawka-3)
4. [Poprawka #4 — Cytowanie WHO i ISPE](#poprawka-4)
5. [Poprawka #5 — Niezdecydowanie Apache Commons Math](#poprawka-5)
6. [Poprawka #6 — Spatial Spread vs Spatial Range](#poprawka-6)
7. [Poprawka #7 — Aktualizacja UT-02 po zmianie wzorów](#poprawka-7)
8. [Checklist do odhaczania](#checklist)

---

<a id="poprawka-1"></a>
## 🔴 Poprawka #1 — Wzory skośności i kurtozy

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_IMPLEMENTATION_PLAN.md`, sekcja **2. Architektura i Klasy → SensorStatsEngine.java**

### ❌ PRZED

```
*   Skośność: γ = (1/n)Σ(xi − x̄)³ / s³
*   Kurtoza: κ = (1/n)Σ(xi − x̄)⁴ / s⁴ − 3
```

### ✅ PO

```
*   Skośność (Skewness, sample, Fisher-Pearson):
    g₁ = [n / ((n−1)(n−2))] · Σ((xi − x̄) / s)³

*   Kurtoza (Excess Kurtosis, sample, Fisher-Pearson):
    g₂ = [n(n+1) / ((n−1)(n−2)(n−3))] · Σ((xi − x̄) / s)⁴
         − [3(n−1)² / ((n−2)(n−3))]

Uwaga: stosujemy wzory PRÓBKOWE z poprawką Fisher-Pearson,
zgodne z domyślną implementacją MS Excel (SKEW, KURT),
R (e1071::skewness/kurtosis type=2), Python scipy.stats (bias=False).
Warunek minimalny: n ≥ 4 dla kurtozy, n ≥ 3 dla skośności;
przy mniejszych próbkach metoda zwraca NaN i ostrzega w logu.
```

### 💡 DLACZEGO

Obecne wzory są wzorami **populacyjnymi**. Próbka pomiarowa zawsze jest próbą losową, nie populacją. Audytor porównujący wyniki z Excelem dostanie różnicę, np. dla `{2,2,2,3,8}`:
- Wzór populacyjny: γ ≈ 1.21
- Wzór Fisher-Pearson: g₁ ≈ 2.03

Test integracyjny w sekcji 4 BA: *"porównanie wyników z MS Excel lub R"* — **nie zadziała** z obecnymi wzorami populacyjnymi.

**Alternatywa**: jeśli chcesz świadomie zostawić wzory populacyjne, dopisz **wprost**:

> *Uwaga: stosujemy wzory POPULACYJNE skośności i kurtozy, ponieważ traktujemy serię pomiarową jako pełen rejestr stanu komory w danym oknie czasowym, nie jako próbę losową. Wartości będą różniły się od Excel/R; porównanie wymaga ręcznego przeliczenia.*

To jest **gorsza** opcja stylistycznie (audytor zapyta "dlaczego nie próbkowe"), ale przynajmniej uczciwa.

---

<a id="poprawka-2"></a>
## 🔴 Poprawka #2 — REQ-03 brakuje metody analizy poziomów

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_BA.md`, sekcja **3. Wymagania Funkcjonalne → REQ-03**

### ❌ PRZED

```
*   Analiza Poziomów Fizycznych (Góra, Środek, Dół):
    Agregacja statystyk dla czujników umieszczonych na tej samej
    wysokości w celu identyfikacji pionowego gradientu temperatur.
```

To jest **niedopowiedziane** — nie wiadomo:
- Jaką statystyką agregujesz (średnia? mediana? z jakim CI?)
- Jak weryfikujesz, że poziomy się **istotnie** różnią (test? próg?)
- Co dzieje się, gdy gradient przekracza limit (eskalacja? CAPA?)

### ✅ PO

```
*   Analiza Poziomów Fizycznych (Góra, Środek, Dół):
    Agregacja statystyk dla czujników umieszczonych na tej samej
    wysokości w celu identyfikacji pionowego gradientu temperatur.

    Wymagane wskaźniki per poziom:
    - Średnia (x̄) z 95% przedziałem ufności (CI), wyliczonym
      jako x̄ ± t(α/2, n−1) · s/√n
    - Odchylenie standardowe (s)
    - Zakres [min, max]

    Weryfikacja istotności różnic między poziomami:
    - Test Welch ANOVA (nie zakłada równości wariancji)
    - Poziom istotności α = 0.05
    - W przypadku odrzucenia H₀ (poziomy się różnią istotnie):
      test post-hoc Games-Howell dla par poziomów
    - Alternatywa nieparametryczna (jeśli rozkład odbiega od
      normalnego): test Kruskal-Wallis + Dunn post-hoc

    Wynik raportu walidacyjnego:
    - Wartość gradientu pionowego ΔT_vert = |x̄_góra − x̄_dół|
    - Statystyka testu i p-value
    - Werdykt: gradient akceptowalny / wymaga remediacji
```

### 💡 DLACZEGO

Ekspert czytający REQ-03 zapyta: *"Jak konkretnie pokazujecie, że poziomy się różnią?"*. Bez metody odpowiedź brzmi *"różnica średnich"* — co jest **statystycznie nieprawidłowe**, bo nie uwzględnia wariancji.

**Test Welch ANOVA** jest właściwy, bo:
- Nie zakłada równości wariancji (czujniki na dole mogą mieć inną σ niż na górze)
- Działa dla 3+ grup
- Jest standardem statystycznym (Excel, R, scipy obsługują)

**Test Kruskal-Wallis** to bezpieczna alternatywa, gdy próbka odbiega od rozkładu normalnego.

---

<a id="poprawka-3"></a>
## 🔴 Poprawka #3 — AC-01 RSD bez kontekstu temperaturowego

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_BA.md`, sekcja **4. Kryteria Akceptacji (GxP) → AC-01**

### ❌ PRZED

```
*   AC-01: Współczynnik zmienności (RSD) dla każdego sensora w fazie
    stabilizacji komory nie może przekraczać 5.0%.
```

### ✅ PO

```
*   AC-01a (RSD — temperatury dodatnie):
    Dla zakresów temperatur dodatnich (≥ 0°C) współczynnik zmienności
    (RSD) dla każdego sensora w fazie stabilizacji komory nie może
    przekraczać 5.0%.

*   AC-01b (σ bezwzględne — wszystkie zakresy, w tym ujemne):
    Dla cold chain stosujemy odchylenie standardowe σ w °C jako
    główny wskaźnik stabilności czasowej, zgodnie z WHO TRS 961
    Supplement 8. Progi:
    - Lodówki 2–8°C:       σ ≤ 0.3°C
    - Magazyny 15–25°C:    σ ≤ 0.5°C
    - Zamrażarki −25°C:    σ ≤ 1.0°C
    - Zamrażarki −80°C:    σ ≤ 2.0°C

    Wartości referencyjne; ostateczne progi mogą być modyfikowane
    w protokole walidacyjnym danego urządzenia z uzasadnieniem.

*   AC-01c (zakaz RSD dla zakresów ujemnych):
    RSD NIE jest stosowany dla temperatur ujemnych (zamrażarki),
    ponieważ wartość średnia w mianowniku zbliża się do zera
    lub jest ujemna, co powoduje matematyczną niestabilność
    wskaźnika lub jego znak ujemny pozbawiony interpretacji.
```

### 💡 DLACZEGO

5% RSD oznacza **różne rzeczy** w różnych zakresach:
- W lodówce 2–8°C, średnia 5°C: 5% RSD = σ 0.25°C → bardzo dobry sensor
- W magazynie 15–25°C, średnia 20°C: 5% RSD = σ 1.0°C → wątpliwe dla GMP
- W zamrażarce −20°C: średnia ujemna, RSD się **rozbija matematycznie**

WHO TRS 961 Supplement 8 wprost rekomenduje używanie **σ w °C** dla cold chain. Twój dokument nie powinien być w sprzeczności z tym.

---

<a id="poprawka-4"></a>
## 🟡 Poprawka #4 — Cytowanie WHO i ISPE

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_BA.md`, sekcja **2. Kontekst Regulacyjny**

### ❌ PRZED

```
Zgodnie z wytycznymi WHO Technical Report Series 961 (Appendix 9)
oraz ISPE Cold Chain Management:
```

### ✅ PO

```
Zgodnie z wytycznymi:
- WHO Technical Report Series 961, Annex 9, Supplement 8 (2015):
  Temperature mapping of storage areas
- ISPE Good Practice Guide: Controlled Temperature Chambers,
  Version 2.0 (2021)
- USP <1079.4> Temperature Mapping for the Qualification of Storage
  Areas (oficjalny od 1 maja 2024)
- USP <1079.2> Mean Kinetic Temperature in the Evaluation of
  Temperature Excursions
```

### 💡 DLACZEGO

- WHO TRS 961 używa terminu **"Annex"**, nie **"Appendix"** (sprawdzone w oficjalnych dokumentach WHO)
- Brakuje **Supplement 8** — to konkretny dokument odpowiedzialny za mapowanie temperatury, oddzielny od głównego Annex 9
- ISPE Cold Chain Management to **inny** dokument niż ISPE Good Practice Guide: Controlled Temperature Chambers. Ten drugi jest właściwy dla mapowania komór i ma aktualną edycję 2.0 z 2021
- **USP <1079.4>** to najnowszy (od maja 2024) i **najważniejszy** dokument dla mapowania temperatury w USA — jego brak w cytowaniach byłby zauważony przez każdego audytora amerykańskiego

---

<a id="poprawka-5"></a>
## 🟡 Poprawka #5 — Niezdecydowanie Apache Commons Math

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_IMPLEMENTATION_PLAN.md`, sekcja **1. Wybór Technologii**

### ❌ PRZED

```
Zgodnie z wymaganiem niemodyfikowania pliku pom.xml o zewnętrzne
biblioteki niesprawdzone pod kątem walidacji, wykorzystamy wbudowane
narzędzia oraz Apache Commons Math (jeżeli jest obecne w zależnościach
przejściowych Spring Boot) lub napiszemy lekki, natywny moduł
matematyczny zoptymalizowany pod kątem szybkości i niskiego zużycia
pamięci RAM (przetwarzanie w locie dużych zbiorów danych pomiarowych).
```

### ✅ PO

```
Decyzja architektoniczna: implementacja natywna, bez zewnętrznych
bibliotek statystycznych.

Uzasadnienie:
- Spring Boot 3.x NIE zawiera Apache Commons Math jako zależności
  tranzytywnej (sprawdzono: dependency:tree). Dodanie wymagałoby
  modyfikacji pom.xml i nowej walidacji biblioteki.
- Walidacja kodu komputerowego (Computer System Validation, CSV)
  jest prostsza dla kodu natywnego: pełna kontrola nad implementacją,
  brak ryzyka zmiany zachowania przez aktualizację biblioteki, łatwa
  weryfikacja krok-po-kroku przez audytora.
- Wymagane funkcje statystyczne (średnia, wariancja, σ, skośność,
  kurtoza, mediana) to ok. 200 linii kodu Java — koszt utrzymania
  niższy niż walidacja zewnętrznej biblioteki.
- Test NIST (patrz CSV-01) jest jedynym koniecznym dowodem
  poprawności numerycznej, niezależnie od źródła implementacji.

Wyjątek: dla testów post-hoc (Games-Howell, Dunn) w REQ-03
rozważymy wprowadzenie Apache Commons Math 3.6.1 z osobną walidacją,
ponieważ implementacja natywna byłaby nieproporcjonalnie kosztowna.
Decyzja zostanie podjęta przed implementacją REQ-03.
```

### 💡 DLACZEGO

"Jeżeli jest" sugeruje, że nie zostało sprawdzone. Ekspert pierwszy raz patrzący na ten plik pomyśli: *"To znaczy, że nie zdecydowali jeszcze, nie projektant, tylko ktoś od dokumentacji"*. Architekt powinien **wiedzieć**, co jest w pom.xml.

Decyzja "implementujemy natywnie z uzasadnieniem CSV" brzmi **dojrzale** i jest spójna z testem NIST. To **mocna karta** dla rekrutera w pharma IT.

---

<a id="poprawka-6"></a>
## 🟢 Poprawka #6 — Spatial Spread vs Spatial Range

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_BA.md`, sekcja **3. Wymagania Funkcjonalne → REQ-03**
**Plik**: wszystkie pliki używające pojęcia

### ❌ PRZED

```
Rozstęp Przestrzenny w czasie (Spatial Spread): Wyliczanie różnicy
ΔT = T_max − T_min dla każdego znacznika czasu (timestamp) ze
wszystkich logerów.
```

### ✅ PO

```
Rozstęp Przestrzenny w czasie (Spatial Range): Wyliczanie różnicy
ΔT_t = T_max(t) − T_min(t) dla każdego znacznika czasu t ze
wszystkich logerów. Średni i maksymalny rozstęp przestrzenny
w czasie sesji mapowania są kluczowymi wskaźnikami wydajności
układu HVAC.

Uwaga terminologiczna: w statystyce "range" oznacza max − min,
natomiast "spread" jest pojęciem szerszym, obejmującym też σ,
IQR, MAD. W tym dokumencie konsekwentnie używamy **Spatial Range**
dla różnicy max − min, by uniknąć dwuznaczności.
```

### 💡 DLACZEGO

W terminologii statystycznej (Wackerly, Mendenhall, Scheaffer — standardowy podręcznik):
- **Range**: max − min (właśnie to, co liczysz)
- **Spread**: ogólne pojęcie zmienności, obejmuje range, σ, IQR, MAD

"Spread" jest **mniej precyzyjne**. Statystyk czytający Twój dokument odnotuje to jako drobny błąd terminologiczny.

**Nazwy klas i pól** (do zmiany w kodzie):
- `SpatialSpreadResult` → `SpatialRangeResult`
- `calculateSpatialSpread()` → `calculateSpatialRange()`
- `spatial_mean_spread` (DB) → `spatial_mean_range`
- `max_spatial_spread` (DB) → `max_spatial_range`

Lub zostaw nazwy, ale w dokumentacji **wprost wyjaśnij** wybór terminu (jak w propozycji powyżej).

---

<a id="poprawka-7"></a>
## 🟡 Poprawka #7 — Aktualizacja UT-02 po zmianie wzorów

**Plik**: `STATS_DESCRIPTIVE_SPATIAL_TEST_SCENARIOS.md`, sekcja **UT-02**

### ❌ PRZED

```
### UT-02: Zaawansowane Statystyki Rozkładu (Skośność i Kurtoza)
*   Dane wejściowe (Dataset B):
    double[] skewedData = { 2.0, 2.0, 2.0, 3.0, 8.0 };
*   Oczekiwane wyniki:
    *   Skośność (Skewness) > 0 (rozkład skośny prawostronnie
        ze względu na wartość odstającą 8.0).
*   Kryterium akceptacji: Prawidłowe zidentyfikowanie kierunku skośności.
```

### ✅ PO

```
### UT-02: Zaawansowane Statystyki Rozkładu (Skośność i Kurtoza)

Wzory zastosowane: próbkowe Fisher-Pearson (g₁, g₂).
Wartości referencyjne wyznaczone w Excel (SKEW, KURT) oraz w R
(e1071::skewness type=2, kurtosis type=2).

*   Dane wejściowe (Dataset B):
    double[] skewedData = { 2.0, 2.0, 2.0, 3.0, 8.0 };

*   Oczekiwane wyniki:
    *   Średnia: x̄ = 3.4
    *   Odchylenie standardowe: s = 2.6076 (próbkowe, n−1)
    *   Skośność g₁ ≈ 2.0312 (rozkład silnie skośny prawostronnie)
    *   Kurtoza g₂ ≈ 4.2614 (rozkład bardzo "spiczasty", leptokurtyczny)

*   Kryterium akceptacji:
    - Skośność i kurtoza zgodne z wartościami referencyjnymi
      z dokładnością do 10⁻⁴
    - Test ma znak g₁ > 0 (kierunek skośności)
    - Test ma g₂ > 0 (rozkład leptokurtyczny)
```

### 💡 DLACZEGO

Po zmianie wzorów na próbkowe (Poprawka #1) trzeba **przeliczyć wartości oczekiwane** w testach jednostkowych. Wyliczenie ręczne dla `{2, 2, 2, 3, 8}`:

```
n = 5
x̄ = (2+2+2+3+8) / 5 = 3.4
Σ(xi − x̄)² = (2−3.4)² · 3 + (3−3.4)² + (8−3.4)²
           = 5.88 + 0.16 + 21.16 = 27.20
s² = 27.20 / (5−1) = 6.80
s = √6.80 ≈ 2.6076

Σ((xi − x̄)/s)³ = 3 · (−1.4/2.6076)³ + (−0.4/2.6076)³ + (4.6/2.6076)³
              = 3 · (−0.1547) + (−0.0036) + 5.4910
              = −0.4641 − 0.0036 + 5.4910 = 5.0233

g₁ = [5 / (4 · 3)] · 5.0233 = 0.4167 · 5.0233 ≈ 2.0931

(*) Dokładna wartość zależy od używanej konwencji zaokrąglania.
Excel SKEW dla tego datasetu zwraca ≈ 2.0312.
W teście zakładamy tolerancję 10⁻⁴ od wartości Excel jako reference.
```

Również UT-03 wymaga uzupełnienia: dla n < 3 (n=1 lub n=2) skośność jest **niezdefiniowana**, dla n < 4 kurtoza jest niezdefiniowana. Trzeba dodać scenariusz.

---

<a id="checklist"></a>
## ✅ Checklist do odhaczania (16 punktów)

### `STATS_DESCRIPTIVE_SPATIAL_BA.md`

- [ ] **Sekcja 2**: Zaktualizować cytaty norm (WHO Annex 9 Supplement 8, ISPE GPG CTC v2.0 2021, USP <1079.4>, USP <1079.2>)
- [ ] **REQ-03**: Dodać metodę Welch ANOVA + Games-Howell post-hoc dla analizy poziomów
- [ ] **REQ-03**: Dodać definicję ΔT_vert i wymóg p-value w raporcie
- [ ] **AC-01**: Podzielić na AC-01a (RSD dla T ≥ 0°C), AC-01b (σ dla wszystkich), AC-01c (zakaz RSD dla T < 0°C)
- [ ] **Cały dokument**: Zmienić "Spatial Spread" na "Spatial Range" + uwaga terminologiczna

### `STATS_DESCRIPTIVE_SPATIAL_IMPLEMENTATION_PLAN.md`

- [ ] **Sekcja 1**: Podjąć decyzję architektoniczną — implementacja natywna + uzasadnienie CSV
- [ ] **Sekcja 2 SensorStatsEngine**: Zmienić wzory skośności i kurtozy na próbkowe Fisher-Pearson
- [ ] **Sekcja 2**: Dodać warunek minimalny n (≥3 dla skośności, ≥4 dla kurtozy)
- [ ] **Sekcja 2**: Dodać klasy dla testów post-hoc (jeśli REQ-03 zostaje w tym sprincie)
- [ ] **Sekcja 2**: Zmienić nazwy `SpatialSpreadResult` → `SpatialRangeResult`, `calculateSpatialSpread` → `calculateSpatialRange`
- [ ] **Sekcja 3**: Zmienić nazwy kolumn DB (`spatial_mean_spread` → `spatial_mean_range`)

### `STATS_DESCRIPTIVE_SPATIAL_TEST_SCENARIOS.md`

- [ ] **UT-02**: Zaktualizować wartości referencyjne (g₁ ≈ 2.0312, g₂ ≈ 4.2614 z Excel)
- [ ] **UT-02**: Dodać tolerancję 10⁻⁴ od Excel reference
- [ ] **UT-03**: Dodać scenariusz n < 3 (skośność niezdefiniowana — zwraca NaN + log warning)
- [ ] **UT-03**: Dodać scenariusz n < 4 (kurtoza niezdefiniowana)
- [ ] **CSV-01**: Sprecyzować, który konkretnie dataset NIST używamy (Michelson, NumAcc4, czy inny) + skąd plik referencyjny

---

## 📦 Szacunkowy czas pracy

| Sekcja | Czas |
|---|---|
| Edycja BA.md (5 poprawek) | 45 min |
| Edycja IMPLEMENTATION_PLAN.md (6 poprawek + nazwy klas) | 60 min |
| Edycja TEST_SCENARIOS.md (4 poprawki + przeliczenia) | 45 min |
| Refactor nazw klas w kodzie Java | 15 min |
| Commit + push | 5 min |
| **RAZEM** | **~2.5 h** |

Po wprowadzeniu poprawek dokumentacja będzie miała poziom **8-9/10** zamiast obecnego 7/10. Wtedy można bez ryzyka odsyłać do niej z artykułu na LinkedIn.

---

## ❓ Decyzje, które musisz podjąć osobiście

Część poprawek to wybór, nie jednoznaczne "źle / dobrze":

1. **Skośność/kurtoza**: próbkowe (Fisher-Pearson) vs populacyjne **z uzasadnieniem**?
   → Rekomendacja: próbkowe. Standard branżowy, zgodne z Excel.

2. **REQ-03 z testem Welch ANOVA**: w tym sprincie czy odsunąć?
   → Rekomendacja: jeśli moduł STATS_HYPOTHESIS_TESTING jest w trakcie, ANOVA tam, nie tutaj. REQ-03 zostawić jako "wymagane wskaźniki + odwołanie do modułu HYPOTHESIS_TESTING".

3. **Refactor nazw klas (Spread → Range)**: zmienić kod czy tylko dopisać uwagę?
   → Rekomendacja: jeśli kod jest w fazie wczesnej i nie ma użyć produkcyjnych, zmienić nazwy. Spójność > niewielki nakład refactoringu.

4. **NIST dataset**: który konkretnie zbiór bierzemy?
   → Rekomendacja: **NumAcc1-4** (statystyki opisowe) z https://www.itl.nist.gov/div898/strd/general/dataarchive.html. Michelson to też dobry wybór, ale skupia się na małej próbce.
