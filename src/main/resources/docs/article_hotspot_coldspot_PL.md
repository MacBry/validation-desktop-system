🌡️ Hotspot i Coldspot w mapowaniu temperatury: pięć metod, pięć różnych werdyktów. Który czujnik wskazujesz audytorowi?

Stoję przed kolejną decyzją projektową w moim systemie walidacyjnym i ta jest bardziej podstępna, niż się wydaje.

Punkt wyjścia jest jasny. Dokumenty regulacyjne — USP <1079.4> (oficjalny od 1 maja 2024) oraz WHO TRS 961, Annex 9, Supplement 8 (2015) — wymagają identyfikacji hotspot i coldspot oraz uzasadnienia rozmieszczenia czujników monitoringu ciągłego w protokole walidacyjnym. WHO formułuje to wprost: mapowanie służy m.in. do identyfikacji stref wymagających działań korygujących, np. modyfikacji rozkładu powietrza, by wyeliminuć hot i cold spots.

Czego natomiast w tych dokumentach **nie ma** — to konkretnego algorytmu statystycznego wyznaczania tych punktów. Wybór metody jest decyzją projektową firmy, wymagającą uzasadnienia w protokole. I to **ten wybór**, a nie same pomiary, decyduje, na który czujnik wskażesz palcem.

Pokażę to na konkretnej próbce danych z mojej symulacji. Lodówka farmaceutyczna 2–8°C, 9 czujników: 4 na górnej półce, 4 na dolnej, 1 w środku. Sesja mapowania 24h, próbkowanie co 5 minut — zgodnie z WHO TRS 961, który dla cold rooms zaleca 24–72h mapowania.

Komora ma realistyczny gradient pionowy ~3°C (góra cieplejsza), szum pomiarowy ~0,15°C oraz cztery „życiowe" wydarzenia w trakcie mapowania:

▪️ T2 (góra NE) — krótkie otwarcie drzwi w 12h, spike do 9,1°C przez 15 minut
▪️ T3 (góra SE) — powolny drift +0,6°C w 24h (słabnący kontakt termiczny)
▪️ T4 (góra SW) — wadliwy cykl odszraniania, 90 minut przy 8,3°C
▪️ B3 (dół SE) — jednorazowy artefakt -0,8°C (chwilowe dotknięcie zimnej ściany podczas montażu)

Oto wyniki dla 9 czujników (°C):

```
Czujnik     AbsMax  AbsMin  Mean   P99    MKT   TOL>8°C  TOL<2°C
T1_top_NW    7.08   6.01   6.50   6.85   6.50    0       0
T2_top_NE    9.10   5.03   5.43   6.11   5.44    8       0   ← spike drzwi
T3_top_SE    7.13   5.94   6.51   7.03   6.51    0       0   ← drift
T4_top_SW    8.33   5.37   5.97   8.29   5.99   15.4     0   ← przekroczenie limitu
C_center     5.39   4.61   5.01   5.35   5.01    0       0
B1_bot_NW    3.98   3.06   3.52   3.88   3.52    0       0
B2_bot_NE    3.67   2.76   3.20   3.53   3.20    0       0   ← najzimniej stale
B3_bot_SE    4.31  -0.80   3.88   4.21   3.89    0      14   ← artefakt montażowy
B4_bot_SW    4.07   3.20   3.61   4.00   3.61    0       0
```

(TOL = Time-Over/Under-Limit, °C·min powyżej 8°C lub poniżej 2°C)

Patrz, co się dzieje, gdy zmieniasz metodę:

🔴 HOTSPOT — pięć metod, TRZY różne werdykty
▪️ Maksimum absolutne → T2 (9,10°C) — łapie spike z otwarcia drzwi
▪️ Średnia arytmetyczna → T3 (6,51°C) — łapie czujnik z driftem
▪️ MKT (Arrhenius) → T3 (6,51°C) — to samo co średnia, bo brak ekstremów
▪️ Percentyl 99 → T4 (8,29°C) — łapie 90-minutowe przekroczenie limitu
▪️ Time-Over-Limit → T4 (15,4 °C·min) — to samo, kwantyfikacja ryzyka

🔵 COLDSPOT — cztery metody, DWA różne werdykty
▪️ Minimum absolutne → B3 (-0,80°C) — łapie artefakt montażowy
▪️ Średnia / Percentyl 1 → B2 (3,20°C / 2,87°C) — łapie czujnik stale najzimniejszy
▪️ Time-Under-Limit → B3 (14 °C·min) — kwantyfikuje przekroczenie

To są **różne fizyczne czujniki**. Postawienie monitoringu ciągłego na T2 vs T3 vs T4 to trzy odmienne decyzje walidacyjne. Każda ma swoje uzasadnienie. Każdą da się obronić przed audytorem. **Ale tylko jedną piszesz w protokole.**

⚙️ Co która metoda mówi (i ukrywa)

❌ Maksimum absolutne (worst-case)
Łapie wszystko, łącznie z artefaktami. T2 stało się „hotspotem" wyłącznie dlatego, że ktoś otworzył drzwi na 15 minut. Konsekwencja: postawisz czujnik monitoringu w miejscu, które jest *przypadkiem* spike'a, nie strefą trwałego ryzyka.

❌ Średnia arytmetyczna
Maskuje dynamikę. Czujnik o stałych 5,0°C i czujnik wahający się 1–9°C mają tę samą średnią. To **nie jest metoda wymieniona w dokumentach regulacyjnych** — przedstawiam ją jako jedną z możliwych do dyskusji.

⚠️ MKT (Arrhenius, ΔH = 83 144 J/mol, R = 8,314 J/(mol·K))
Nieliniowa średnia ważona degradacją chemiczną. Dla hotspotu w lekach termolabilnych ma głębokie uzasadnienie chemiczne. Ale matematycznie tłumi niskie temperatury — **nie używaj jej do coldspotu**.

**Ważna uwaga regulacyjna**: USP <1079.2> (oficjalny od grudnia 2020) ograniczył okno danych dla obliczania MKT. Dla CRT (Controlled Room Temperature, 20–25°C) jest to maksymalnie 30 dni. **Dla CCT (Controlled Cold Temperature, 2–8°C) — tylko 24 godziny**. Najczęstsze nadużycie MKT to liczenie z 52 tygodni danych, co matematycznie „rozcieńcza" wyniki i ukrywa rzeczywiste nadużycia termiczne. Mapowanie 24h w lodówce CCT jest dokładnie zgodne z tym oknem.

⚠️ Percentyle (P99 / P01)
**Podejście niestandardowe, oparte na ogólnej praktyce statystycznej** — nie znalazłem odniesienia do percentyli w dokumentach USP, WHO ani ISPE jako kanonicznej metody mapowania. Mocna strona: odrzuca skrajne 1% jako szum. Słaba: 10-minutowe wyłączenie zasilania przy 24h mapowania to 0,69% danych — wpada w odrzucany ogon. Im dłuższe mapowanie, tym więcej realnych awarii można „statystycznie zakopać". Wybór progu (99? 95?) jest arbitralny i wymaga uzasadnienia w protokole.

⚠️ Time-Over-Limit (indeks całkowy)
**Własna propozycja** inspirowana koncepcją kumulacji ekspozycji termicznej (która jest fundamentem MKT). Mierzy iloczyn głębokości i czasu przekroczenia limitu. Najbliżej realnemu ryzyku produktowemu. Problem: jeśli żadna strefa nie przekrózyła limitu, wszystkie czujniki dostają 0 — metoda nie umie wskazać hotspotu w stabilnej komorze. Działa jako *dopełniacz*, nie jako jedyne kryterium.

🚫 Co pomija klasyczna analiza per-czujnik

Wszystkie pięć metod traktuje każdy czujnik osobno. Ale czujniki są w 3D — w siatce w komorze. Co, jeśli prawdziwy hotspot leży **między** czujnikami, np. 15 cm od T2?

Profesjonalne narzędzia mapowania (Kaye, Vaisala viewLinc, Ellab) używają **interpolacji przestrzennej** — najczęściej krigingu albo radialnych funkcji bazowych (RBF) — żeby oszacować pole temperaturowe poza punktami pomiarowymi. Hotspot to wtedy nie czujnik, tylko interpolowany punkt w przestrzeni komory.

To wykracza poza wymagania dokumentów regulacyjnych — **nie znalazłem w USP <1079.4>, WHO TRS 961 ani ISPE Good Practice Guide formalnego wymogu interpolacji przestrzennej**. To „nice to have" oparte na zdrowej praktyce inżynierskiej.

Konsekwencja walidacyjna jest jednak realna: jeśli ustawisz monitoring na czujniku „z mapowania", a rzeczywisty hotspot jest między czujnikami — monitorujesz prawie to, co trzeba. Doświadczony audytor to zauważy.

🎯 Mój obecny kierunek (jeszcze nie ostateczny)

Hybryda z trzema warstwami, gdzie każda warstwa jest osobno uzasadniona w protokole:

▪️ **Coldspot**: AbsMin + TOL<L jako kontrola. Krystalizacja wody ma skutek natychmiastowy — sekundowy spadek poniżej 0°C musi być wyłapany. Ale ostateczna decyzja o lokalizacji monitoringu powinna uwzględniać też częstotliwość zdarzenia (czy to artefakt, czy strefa systemowo zimna).

▪️ **Hotspot**: MKT + P99 + TOL>U widziane łącznie, nie jako konkurujące werdykty. Jeśli wszystkie trzy wskazują ten sam czujnik — silny sygnał. Jeśli wskazują różne — protokół musi wyjaśnić, dlaczego wybrano ten, a nie inny.

▪️ **Warstwa przestrzenna (eksperymentalnie)**: interpolacja RBF na siatce 9 czujników, raportowana jako dodatkowy wynik, z jasnym oznaczeniem „wykracza poza wymagania regulacyjne".

❓ Pytanie do osób, które pisały protokoły mapowania pod realny audyt

Czy w Waszych protokołach jest jeden „święty" algorytm wyznaczania hotspotu, czy hybryda z uzasadnieniem per decyzja? I czy widzieliście kiedyś, żeby audytor pytał o interpolację przestrzenną, czy to wciąż wyłącznie wewnętrzna dobra praktyka?

Chętnie posłucham. 💬

📚 Źródła
• USP <1079.4> Temperature Mapping for the Qualification of Storage Areas (USP-NF, oficjalny od 2024-05-01)
• USP <1079.2> Mean Kinetic Temperature in the Evaluation of Temperature Excursions (USP-NF, oficjalny od 2020-12-01)
• USP <1079.3> Monitoring Devices — Time, Temperature, and Humidity (USP-NF)
• USP <1079> Risks and Mitigation Strategies for the Storage and Transportation of Finished Drug Products (USP-NF)
• WHO Technical Report Series 961, Annex 9, Supplement 8: Temperature mapping of storage areas (maj 2015)
• ISPE Good Practice Guide: Controlled Temperature Chambers Version 2.0 (2021)
• ICH Q1A(R2) Stability Testing of New Drug Substances and Products
• PIC/S Guide to Good Distribution Practice PE 011-1 (2014)

#GMP #GxP #FDA #ColdChain #Validation #Pharma #DataAnalysis #ThermalMapping
