# Raport odchyleń oprogramowania — analiza SPC i wskaźników zdolności

**Dokument:** Software Defect Report (CSV / GAMP 5)
**Data analizy:** 2026-07-08
**Analizowany artefakt:** `Raport_Rewalidacji_GxP_32123.pdf` (pakiet `PAKIET_WALIDACYJNY_GxP_32123_2026-07-08_17-55`)
**Obiekt walidacji:** Liebherr, nr inw. 32123 — komora chłodnicza (krew pełna i KKCz, wymóg 2–6°C; zakres pracy komory 3–8°C)
**Moduł:** Validation Desktop — silnik statystyczny SPC / GUM
**Autor analizy:** przegląd kodu źródłowego + weryfikacja obliczeń

---

## 1. Cel i zakres

Celem analizy była odpowiedź na pytanie, czy wnioski raportu rewalidacji
(14 naruszeń reguł Nelsona, „doskonała zdolność procesu Cpk ≥ 2,25",
FAIL jednorodności pionowej) wynikają z rzeczywistego stanu danych, czy z wad
oprogramowania. Przeanalizowano kod źródłowy silnika SPC oraz zweryfikowano
obliczenia względem surowych danych z sekcji 5 raportu.

## 2. Weryfikacja poprawności obliczeń (co działa prawidłowo)

Aby oddzielić realne wady od poprawnie działających elementów, potwierdzono:

- **Stałe kart Shewharta dla n=5 są prawidłowe:** `A3=1,427`, `B4=2,089`,
  `B3=0` — `ControlChartCalculator.java:10-13`.
- **Granice kontrolne zgadzają się z danymi:** dla Dół‑Przód‑Prawy
  UCL(X‑bar) = 5,76 + 1,427·0,080 = **5,87°C** (raport: 5,87 ✔),
  UCL(S) = 2,089·0,080 = **0,167°C** (raport: 0,167 ✔).
- **Wykrywanie Reguły 1** (`NelsonRulesDetector.java:41`) oraz **formuła Cpk/Cpu/Cpl**
  (`SpcEngine.java:21-26`) są matematycznie poprawne.
- **Odtworzenie średnich podgrup** z danych sekcji 5 daje dokładnie te same 14 naruszeń
  Reguły 1, które wykazał raport.

**Wniosek:** liczby nie są wynikiem błędu arytmetycznego. Problem leży w **założeniach
i doborze parametrów**, które kształtują te liczby — opisano poniżej.

## 3. Rejestr odchyleń

### DEF-01 — Cpk liczony względem limitów komory, nie wymogu produktu (KRYTYCZNY)

- **Lokalizacja:** `RevalidationReportPdfRenderer.java:70-71`;
  `StatisticalSectionRenderer.java:61-62`; `RegimeAwareSectionRenderer.java:121-122`;
  `SpcEngine.calculateCapability` (`SpcEngine.java:7`).
- **Opis:** wskaźniki Cp/Cpk otrzymują granice specyfikacji z zakresu pracy komory
  (`chamber.getMinOperatingTemp()` / `getMaxOperatingTemp()` = 3,0 / 8,0°C), a nie
  z wymogu przechowywania produktu (krew: 2–6°C). Potwierdza to komentarz w kodzie:
  `MetrologicalStatsService.java:389` — „Dolny limit **komory**".
- **Skutek w raporcie 32123:** dla hotspotu Dół‑Przód‑Prawy raportowano Cpk = 3,08
  („doskonała zdolność"). Przy właściwym limicie górnym 6°C:
  Cpu = (6 − 5,76) / (3 · 0,243) ≈ **0,33** — proces **niezdolny**.
  Dane realnie osiągają **6,3°C > 6°C** (ekskursja dla krwi).
- **Ryzyko:** oprogramowanie generuje fałszywie pozytywny („zielony") wniosek
  o zdolności procesu i **maskuje rzeczywistą ekskursję temperaturową** dla materiału
  krytycznego. Bezpośredni wpływ na decyzję walidacyjną i bezpieczeństwo produktu.
- **Klasyfikacja:** Krytyczny (Critical) — wpływ na integralność wniosku GxP i wyrób.

### DEF-02 — Podgrupowanie sekwencyjne danych autoskorelowanych zawyża liczbę naruszeń (WYSOKI)

- **Lokalizacja:** `ControlChartCalculator.java:33-45`.
- **Opis:** podgrupy tworzone są jako arbitralne bloki 5 kolejnych pomiarów
  (`System.arraycopy(values, i*5, subgroup, 0, 5)`). Dane temperaturowe co 3 h są
  silnie autoskorelowane, więc odchylenie **wewnątrz** podgrupy (0,08–0,10°C) jest
  znacznie mniejsze od zmienności **całkowitej** (0,16–0,24°C). Granice X‑bar liczone
  z małej zmienności wewnątrzpodgrupowej stają się sztucznie wąskie, a każdy powolny
  dryf międzydobowy natychmiast przekracza limit jako Reguła 1.
- **Skutek w raporcie 32123:** znaczna część z 14 naruszeń Reguły 1 to artefakt metody
  (nadmiar fałszywych alarmów SPC na danych autoskorelowanych), a nie 14 niezależnych
  zdarzeń procesowych.
- **Zalecenie:** zastosować kartę I‑MR (wartości indywidualne) dla badań stabilności
  lub racjonalne podgrupowanie zsynchronizowane z cyklem dobowym; ewentualnie
  uwzględnić autokorelację (np. karty EWMA/CUSUM lub model resztowy).
- **Klasyfikacja:** Wysoki (High) — wpływ na wiarygodność liczby i charakteru naruszeń.

### DEF-03 — Reguły Nelsona 2–4 strukturalnie nieaktywne przy małej liczbie podgrup (ŚREDNI)

- **Lokalizacja:** `NelsonRulesDetector.java:51` (Reguła 2, `i >= 8`),
  `:74` (Reguła 3), `:98` (Reguła 4, `i >= 13`), warunek trendu `:80-83`.
- **Opis:**
  - Reguła 2 wymaga 9 kolejnych punktów → przy 8 podgrupach **nigdy nie zadziała**.
  - Reguła 4 wymaga 14 punktów → niemożliwe przy 8 podgrupach.
  - Reguła 3 (trend 6 pkt) używa ostrych nierówności (`curr <= prev`); przy danych
    zaokrąglonych do 0,1°C remisy zrywają trend, więc praktycznie nie odpala.
- **Skutek w raporcie 32123:** mimo wyraźnego dryfu diagnostycznie trafniejsze reguły
  trendu/serii są wyłączone; cały sygnał spada na Regułę 1, przez co dryf jest opisany
  jako „przekroczenie granic 3‑sigma" zamiast jako trend.
- **Zalecenie:** dokumentować minimalną liczbę podgrup wymaganą do sensownej detekcji
  reguł 2–4; rozważyć złagodzenie ostrej nierówności w Regule 3 albo detekcję trendu
  na nieзаokrąglonych wartościach.
- **Klasyfikacja:** Średni (Medium) — wpływ na interpretację, nie na poprawność liczb.

### DEF-04 — Ciche odrzucanie niepełnej końcowej podgrupy (ŚREDNI, latentny)

- **Lokalizacja:** `ControlChartCalculator.java:25` — `int m = n / SUBGROUP_SIZE;`.
- **Opis:** dzielenie całkowite powoduje, że gdy liczba pomiarów nie jest wielokrotnością
  5, końcowe 1–4 pomiary są **po cichu pomijane** w analizie SPC.
- **Skutek w raporcie 32123:** brak (dokładnie 40 pomiarów = 8×5, nic nie odrzucono).
  Wada latentna, ujawni się przy innych zbiorach.
- **Ryzyko:** naruszenie integralności/kompletności danych GxP — wszystkie dane powinny
  być ocenione, a wykluczenie udokumentowane i uzasadnione.
- **Klasyfikacja:** Średni (Medium) — integralność danych.

## 4. Ocena wniosków raportu

| Wniosek w raporcie | Błąd arytmetyki? | Zniekształcony przez oprogramowanie? | Odchylenia |
|---|---|---|---|
| 14 naruszeń Nelsona (same Reguła 1) | Nie | Tak | DEF-02, DEF-03 |
| „Doskonała zdolność Cpk ≥ 2,25" | Nie | Tak, poważnie | DEF-01 |
| FAIL jednorodności pionowej (Kruskal–Wallis, p=0,0000) | Nie | Test niezależny — prawdopodobnie wiarygodny | — |
| Ekskursja hotspotu do 6,3°C | Dane realne | Ukryta w ocenie zdolności | DEF-01 |

**Podsumowanie:** dryf temperatury i niejednorodność pionowa są realne w danych.
Natomiast (a) liczba i charakter naruszeń Nelsona zależą silnie od dyskusyjnej metody
podgrupowania (DEF-02/03), a (b) uspokajający wniosek o „doskonałej zdolności procesu"
jest efektem błędu w doborze limitów specyfikacji (DEF-01) i ukrywa ekskursję >6°C
dla krwi — to najpoważniejsza wada.

## 5. Zalecane działania (CAPA — oprogramowanie)

1. **DEF-01 (Krytyczny):** liczyć Cp/Cpk względem limitów przechowywania produktu
   (najwęższego obowiązującego kryterium akceptacji), a nie zakresu pracy komory;
   dla obiektów z wymogiem mapowania wymusić podanie limitów produktu. Zweryfikować
   również konfigurację komory (zakres 3–8°C szerszy niż wymóg produktu 2–6°C).
2. **DEF-02 (Wysoki):** wprowadzić kartę I‑MR lub racjonalne podgrupowanie dla badań
   stabilności; udokumentować dobór metody wobec autokorelacji danych.
3. **DEF-03 (Średni):** określić i walidować minimalną liczbę podgrup dla reguł 2–4;
   uodpornić detekcję trendu na zaokrąglenia.
4. **DEF-04 (Średni):** obsłużyć niepełną końcową podgrupę (ocenić lub jawnie i w sposób
   udokumentowany wykluczyć), zamiast cichego odrzucenia.
5. Po korektach: rewizja raportu 32123 i ponowna ocena decyzji walidacyjnej z limitami
   produktu (2–6°C).

## 6. Odniesienia do kodu

- `service/stats/ControlChartCalculator.java` — podgrupowanie, granice Shewharta.
- `service/stats/NelsonRulesDetector.java` — detekcja reguł 1–4.
- `service/stats/SpcEngine.java` — Cp/Cpk.
- `service/MetrologicalStatsService.java` — statystyki skorygowane, przekazanie lsl/usl.
- `service/pdf/RevalidationReportPdfRenderer.java` — źródło limitów (komora).
- `service/pdf/section/StatisticalSectionRenderer.java`,
  `service/pdf/section/RegimeAwareSectionRenderer.java` — renderowanie SPC/zdolności.
