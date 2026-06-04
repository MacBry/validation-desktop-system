# Implementacja hybrydowej detekcji Hotspot/Coldspot w Spring Boot — Java 21, sealed strategies i dwa bugi, o których warto opowiedzieć

*Przegląd warstwy detekcji w moim validation-system, z pełnym źródłem i dwoma bugami, które musiałem naprawić, zanim kod był uczciwy co do własnych ograniczeń.*

---

W poprzednim artykule argumentowałem, że detekcja hotspot/coldspot w mapowaniu GMP łańcucha chłodniczego to nie pojedynczy algorytm — to wybór metodyczny, który determinuje wynik. Pięć metod dało trzy różne werdykty na tym samym zbiorze danych. Wniosek był taki, że system musi ewaluować wiele metod i produkować konsensus, którego audytor będzie w stanie bronić.

Ten artykuł jest o przełożeniu tej argumentacji na kod. Konkretnie: moduł Spring Boot, który łączy pięć strategii, głosuje między nimi i odmawia kłamania na temat zdegenerowanych przypadków.

Pokażę decyzje projektowe, które mają znaczenie, bugi, które złapałem, a pełne źródło jest poniżej. Java 21, bez zewnętrznych zależności poza JDK.

---

## Kształt problemu

Każda runda mapowania produkuje N czujników (zwykle 9 do 27) ze wstępnie zagregowanymi statystykami. Warstwa detekcji musi:

1. Niezależnie zastosować każdą zarejestrowaną strategię.
2. Zwrócić werdykty per-strategia do audit trail — każdą rozważaną metodę, nie tylko zwycięzcę.
3. Zagregować w konsensus, który ma jasno zdefiniowane zachowanie dla remisów i dla "detekcja niemożliwa".
4. Być niemożliwa do rozszerzenia po cichu — każda metoda, która może wystąpić w protokole, musi przejść przez code review.

Ostatni punkt wyklucza kilka oczywistych projektów. Czytanie nazwy metody z YAML-a i refleksja do klasy? Szybkie do wdrożenia, niemożliwe do audytu. Mapa String → Function? Działa na demo, brak gwarancji kompilacji, że zarejestrowane strategie pokrywają zarówno hotspot, jak i coldspot.

---

## Model danych — niezmienialny, wstępnie zagregowany

Warstwa detekcji nigdy nie widzi surowych punktów pomiarowych. Widzi agregaty per czujnik:

```java
public record SensorStats(
        String sensorId,
        double absMax,
        double absMin,
        double mean,
        double p99,
        double p01,
        double mkt,
        double tolHi,   // Time-Over-Limit (°C·min powyżej górnej granicy)
        double tolLo    // Time-Under-Limit (°C·min poniżej dolnej granicy)
) {
    public double get(StatField field) {
        return switch (field) {
            case ABS_MAX -> absMax;
            case ABS_MIN -> absMin;
            case MEAN    -> mean;
            case P99     -> p99;
            case P01     -> p01;
            case MKT     -> mkt;
            case TOL_HI  -> tolHi;
            case TOL_LO  -> tolLo;
        };
    }

    public enum StatField {
        ABS_MAX, ABS_MIN, MEAN, P99, P01, MKT, TOL_HI, TOL_LO
    }
}
```

Dwie rzeczy tutaj są celowe. Dispatcher `get(StatField)` to switch expression, nie Mapa — kompilator wymusza, że każde pole ma gałąź, więc dodanie nowego pola jest błędem kompilacji, dopóki każdy konsument nie zostanie zaktualizowany. A całe `record` jest immutable, co ma znaczenie, gdy ten obiekt będzie serializowany do JSON-a w audit trail i podpisywany.

---

## Interfejs strategii — sealed

Kontrakt jest mały. Każda strategia deklaruje, które pole odczytuje, czy jest metodą hotspot czy coldspot, i produkuje `Verdict`:

```java
public sealed interface ExtremeDetectionStrategy
        permits AbsMaxStrategy, AbsMinStrategy, MeanStrategy,
                MktStrategy, PercentileStrategy, TimeOverLimitStrategy {

    String methodCode();           // stabilne ID, np. "ABS_MAX_HOTSPOT"
    String displayName();          // ludzka etykieta dla protokołu
    boolean isHotspot();
    SensorStats.StatField field();

    default Optional<Verdict> apply(List<SensorStats> stats) { ... }
    default boolean isDegenerate(SensorStats s) { return false; }

    record Verdict(
            String methodCode,
            String methodName,
            String winnerSensorId,
            double value,
            boolean isHotspot
    ) {}
}
```

Słowo kluczowe `sealed` robi tu robotę regulacyjną. Każdy, kto dodaje nową metodę detekcji, musi:

1. Stworzyć klasę.
2. Zaktualizować klauzulę `permits` w `ExtremeDetectionStrategy`.

Obie zmiany dzieją się w tym samym pull requeście. Diff pojawia się w code review. Kompilator odmawia akceptacji anonimowych klas strategii spoza paczki. To jest dokładnie ta właściwość projektowa, której kod GxP potrzebuje — brak mechanizmu, żeby nieautoryzowane metody przeniknęły do produkcji.

> [!TIP]
> Jeśli budujesz projekt w środowisku ze starszą wersją językową (np. JDK 16 lub niższym), które nie obsługuje jeszcze w pełni klas zapieczętowanych (sealed), możesz po prostu usunąć słowo kluczowe `sealed` oraz klauzulę `permits`. Kod zachowa całą funkcjonalność, a interfejs stanie się standardowym kontraktem.


Każda konkretna strategia to potem kilka linii:

```java
public final class AbsMaxStrategy implements ExtremeDetectionStrategy {
    @Override public String methodCode()           { return "ABS_MAX_HOTSPOT"; }
    @Override public String displayName()          { return "Absolute Maximum (worst-case)"; }
    @Override public boolean isHotspot()           { return true; }
    @Override public SensorStats.StatField field() { return SensorStats.StatField.ABS_MAX; }
}
```

Pięć linii per metoda. Cała reszta żyje w domyślnej implementacji `apply()`.

---

## Bug #1 — problem samych zer

Oto oryginalna `apply()`, którą napisałem najpierw:

```java
default Verdict apply(List<SensorStats> stats) {
    var f = field();
    var winner = isHotspot()
            ? stats.stream().max((a, b) -> Double.compare(a.get(f), b.get(f)))
            : stats.stream().min((a, b) -> Double.compare(a.get(f), b.get(f)));
    return new Verdict(methodCode(), displayName(),
            winner.get().sensorId(), winner.get().get(f), isHotspot());
}
```

Uruchamiam na 9-czujnikowym demo. Time-Under-Limit zwraca `T1_top_NW` z wartością `0.00`.

T1 jest na górnej półce — nigdy nie zbliżył się do dolnej granicy. Żaden inny czujnik też nie, z wyjątkiem B3 (który jest faktycznym coldspotem). Co się stało: każdy czujnik poza B3 ma `tolLo = 0.0`, a `Stream.min` na morzu zer po prostu zwraca pierwszy napotkany. Verdict jest technicznie poprawny: T1 *ma* minimalne `tolLo`. Jest zremisowany z siedmioma innymi czujnikami przy dokładnie zerze.

Ten Verdict, jeśli zostanie wysłany, byłby błędny w najgorszy możliwy sposób. Audytor czytający protokół zobaczyłby "Detekcja TUL: T1_top_NW" i wywnioskowałby, że górna półka jest najzimniejszą częścią komory. Zarówno wartość (0.00), jak i absurdalność wyniku byłyby niewidoczne dla kogokolwiek uruchamiającego automatyczne sprawdzenie.

Naprawa polega na uczynieniu zdegenerowanych przypadków jawnymi. Strategie nadpisują `isDegenerate(SensorStats s)`, aby zadeklarować, które czujniki nie niosą sygnału w ich metodzie, a `apply()` zwraca `Optional.empty()`, gdy nie pozostał żaden sygnał:

```java
default Optional<Verdict> apply(List<SensorStats> stats) {
    var meaningful = stats.stream()
            .filter(s -> !isDegenerate(s))
            .toList();

    if (meaningful.isEmpty()) {
        return Optional.empty();   // jawne "brak detekcji"
    }
    // ... wybór zwycięzcy z meaningful
}
```

Dla Time-Over/Under-Limit "brak sygnału" oznacza, że czujnik pozostał w granicach:

```java
@Override
public boolean isDegenerate(SensorStats s) {
    return field() == SensorStats.StatField.TOL_HI ? s.tolHi() == 0.0
                                                   : s.tolLo() == 0.0;
}
```

Teraz demo jest uczciwe: gdy żaden czujnik nie przekracza swojego limitu, TOL nie wnosi żadnego głosu, zamiast wybierać arbitralnego zwycięzcę.

To mała zmiana na poziomie API (typ powrotu zmienia się z `Verdict` na `Optional<Verdict>`), ale propaguje się wszędzie poniżej. Serwis konsensusu musi obsłużyć puste werdykty. Audit trail musi odróżniać "ta metoda została próbowana i nie zwróciła detekcji" od "ta metoda nie była próbowana". To jest właściwy trade-off — API teraz modeluje rzeczywistość.

---

## Bug #2 — porównywanie °C z °C·min

Serwis konsensusu agreguje werdykty per metoda przez głosowanie większościowe. Przy pięciu metodach hotspot spodziewałem się okazjonalnych remisów — a rozstrzyganie remisów to dokładnie ten rodzaj rzeczy, który łatwo zepsuć.

Moja pierwsza próba to był kuszący one-liner:

```java
// Rozstrzygnij remis przez najwyższą wartość ekstremalną
.thenComparing(e -> extremeValueFor(e.getKey(), verdicts, isHotspot))
```

Kompiluje się. Uruchamia. Na demo:

- Mean i MKT głosują na T3 (wartość: 6.51 °C)
- P99 i TOL głosują na T4 (P99: 8.29 °C, **TOL: 15.40 °C·min**)
- AbsMax głosuje na T2

Remis: T3 i T4, obie po 2 głosy. Rozstrzygnięcie "najwyższa wartość" wybiera T4, bo 15.40 > 6.51. Ale 15.40 jest w *°C·min*, to całka przekroczenia po czasie. 6.51 jest w *°C*. Nie można ich porządkować numerycznie — nie są w tej samej przestrzeni.

T4 akurat jest właściwą odpowiedzią w tym przypadku (miał 90-minutową ekskursję powyżej 8°C, co jest bardziej niepokojące niż stabilne 6.51°C T3). Ale rozstrzygnięcie doszło tam przez porównanie z niezgodnymi jednostkami. Zmień długość mapowania z 24h na 72h, a całka rośnie; zmień ją na 1h, a maleje; werdykt mógłby się przewrócić bez żadnej zmiany w fizyce.

Naprawa to rozstrzygnięcie remisu przez **priorytet metodyczny**, nie surową wartość:

```java
private static final List<String> METHOD_PRIORITY = List.of(
        "TOL_HI_HOTSPOT", "TOL_LO_COLDSPOT",
        "ABS_MAX_HOTSPOT", "ABS_MIN_COLDSPOT",
        "MKT_HOTSPOT",
        "P99_HOTSPOT", "P01_COLDSPOT",
        "MEAN_HOTSPOT", "MEAN_COLDSPOT"
);
```

Kolejność: metody bezpośrednio powiązane z ryzykiem regulacyjnym (TOL, absolutne ekstrema) wyprzedzają statystyczne podsumowania. Uzasadnienie jest w komentarzu, a samo uporządkowanie żyje w jednej widocznej stałej — nie zakopane w lambdzie. Audytor pytający "jak rozstrzygnęliście remis?" dostaje jednolinijkową odpowiedź wskazującą na listę.

```java
private static String tieBreakByMethodPriority(
        List<String> candidates,
        List<Verdict> verdicts) {

    for (String method : METHOD_PRIORITY) {
        var pick = verdicts.stream()
                .filter(v -> v.methodCode().equals(method))
                .filter(v -> candidates.contains(v.winnerSensorId()))
                .findFirst();
        if (pick.isPresent()) return pick.get().winnerSensorId();
    }
    return candidates.get(0);
}
```

Wynik dla demo jest identyczny (T4 wygrywa) — ale teraz z właściwego powodu.

---

## Raport konsensusu

Agregacja produkuje rekord, który ląduje wprost w audit trail:

```java
public record ConsensusReport(
        boolean isHotspot,
        String consensusSensorId,
        double consensusStrength,         // ułamek zgadzających się (0.0–1.0)
        Map<String, Integer> votesByCandidate,
        List<Verdict> allVerdicts          // werdykty wszystkich metod
) {
    public boolean hasDetection() { return consensusSensorId != null; }
    public boolean isUnanimous()  { return consensusStrength == 1.0; }
    public boolean isWeak()       { return consensusStrength < 0.5; }
}
```

`isWeak()` to fragment, na którym zależy protokołowi. Słaby konsensus (< 50% zgodności) niczego nie decyduje — eskaluje do QA review. System mówi "tutaj pięć metod, wskazały trzy różne czujniki, musisz wybrać i uzasadnić".

Uruchomienie demo:

```
HOTSPOT — werdykty per-metoda:
  Absolute Maximum (worst-case)         -> T2_top_NE  (9.10)
  Mean (steady-state)                   -> T3_top_SE  (6.51)
  MKT (Arrhenius, ΔH=83144 J/mol)       -> T3_top_SE  (6.51)
  Percentile 99                         -> T4_top_SW  (8.29)
  Time-Over-Limit (>U)                  -> T4_top_SW  (15.40)

  KONSENSUS: T4_top_SW (siła: 40%)
  Rozkład głosów: {T3=2, T4=2, T2=1}
  ⚠ SŁABY KONSENSUS — wymagana ocena QA.
```

40% siły flaguje to jako przypadek wymagający ludzkiego osądu, z pełnym rozkładem głosów zachowanym dla QA managera do rozważenia.

---

## Czego seria artykułów nie pokazuje (i dlaczego)

Warstwa interpolacji przestrzennej — RBF nad siatką 9 czujników — to następny sprint. Prototypuję ją jako mikroserwis w Pythonie (SciPy `RBFInterpolator` to jedna linia; ekwiwalenty Javowe w Apache Commons Math słabo obsługują nieregularne siatki 3D). Strona Spring Boot będzie posiadała orkiestrację, audit trail i persystencję; strona Pythona robi czystą matematykę. Czystszy podział niż próba wnoszenia metod numerycznych do kodu Javowego dla 9-punktowej siatki.

Pełne źródło warstwy detekcji (wszystko z tego artykułu, plus runner demo) jest w moim repozytorium GitHub. Kod kompiluje się na JDK 21 bez zewnętrznych zależności — wklej pliki w drzewo `src/main/java` i działa.

→ [github.com/MacBry/validation-system](https://github.com/MacBry/validation-system)

---

## Co chciałbym wiedzieć

Jeśli budowałeś podobne warstwy detekcji w oprogramowaniu regulowanym — jaka jest Twoja polityka rozstrzygania remisów, gdy wiele metod się nie zgadza? I: czy traktujesz "brak detekcji" jako wynik pierwszej klasy, czy pozwalasz API zwrócić bezsensownego zwycięzcę?

Wzorzec Optional/empty wydaje mi się słuszny, ale jest inwazyjny w całej bazie kodu. Ciekaw, czy ktoś ma lżejszy projekt, który nadal trzyma problem "samych zer" z dala od audit trail.

#Java #SpringBoot #SoftwareArchitecture #GxP #ValidationSystem #DesignPatterns #JDK21
