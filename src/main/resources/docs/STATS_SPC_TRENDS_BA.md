# Analiza Biznesowa (BA) - Statystyczna Kontrola Procesu (SPC) i Analiza Trendów

## 1. Cel Biznesowy
Dostarczenie obiektywnych wskaźników zdolności urządzeń chłodniczych/magazynowych do utrzymania zadanych parametrów środowiskowych. SPC pozwala na prognozowanie awarii (np. stopniowe zużycie czynnika chłodniczego) oraz potwierdzenie jakości wskaźnikami liczbowymi.

## 2. Kontekst Walidacyjny i Regulacyjny (ICH Q9, ICH Q10)
Zgodnie z wytycznymi **ICH Q10 (Pharmaceutical Quality System)**:
*   Producenci leków muszą wdrożyć system monitorowania wydajności procesów i jakości produktów.
*   Zdolność urządzeń do utrzymania parametrów krytycznych (Critical Process Parameters - CPP), takich jak temperatura, musi być mierzona za pomocą wskaźników zdolności procesu.

## 3. Wymagania Funkcjonalne

### REQ-01: Obliczanie Wskaźników Zdolności Procesu ($C_p$, $C_{pk}$)
System musi obliczać wskaźniki na podstawie zadanych Limitów Specyfikacji (Lower Specification Limit - LSL, Upper Specification Limit - USL):
*   **$C_p$ (Zdolność potencjalna):** Stosunek szerokości specyfikacji do zmienności procesu ($6\sigma$):
    $$C_p = \frac{USL - LSL}{6\sigma}$$
*   **$C_{pk}$ (Zdolność rzeczywista):** Uwzględnia przesunięcie średniej procesu względem środka specyfikacji:
    $$C_{pk} = \min\left(\frac{USL - \mu}{3\sigma}, \frac{\mu - LSL}{3\sigma}\right)$$

*Kryteria GxP dla $C_{pk}$:*
*   $C_{pk} \ge 1.33$: Proces wysoce wydajny i bezpieczny (rekomendowany).
*   $1.00 \le C_{pk} < 1.33$: Proces akceptowalny, ale wymagający stałego nadzoru.
*   $C_{pk} < 1.00$: Proces niewydajny. Istnieje wysokie ryzyko wyjścia temperatury poza limity (OOS - Out of Specification).

### REQ-02: Karta Kontrolna Shewharta (X-bar / S) oraz Reguły Nelsona (Nelson Rules)
*   **Wizualizacja:** Średnie podgrup czasowych ($X\text{-bar}$, dla rozmiaru podgrupy $n=5$) oraz ich odchylenia standardowe ($S$) muszą być prezentowane na wykresach kontrolnych.
*   **Wyznaczanie granic:** Automatyczne wyliczanie Linii Centralnej ($CL$) oraz Górnej i Dolnej Granicy Kontrolnej ($UCL, LCL$) w oparciu o statystyczne stałe pomocnicze dla podgrup:
    - Dla karty $X\text{-bar}$: $CL = \overline{\overline{X}}$, $UCL = \overline{\overline{X}} + A_3 \bar{S}$, $LCL = \overline{\overline{X}} - A_3 \bar{S}$ (gdzie $A_3 = 1.427$ dla $n=5$).
    - Dla karty $S$: $CL = \bar{S}$, $UCL = B_4 \bar{S}$, $LCL = B_3 \bar{S}$ (gdzie $B_4 = 2.089, B_3 = 0.0$ dla $n=5$).
*   **Detekcja OOC (Out of Control) za pomocą Reguł Nelsona:** W celu wykrycia niestabilności procesu (zakłóceń o charakterze systemowym), system automatycznie weryfikuje następujące reguły:
    - **Reguła 1:** Jeden punkt poza granicami $\pm 3\sigma$ (poza $UCL$/$LCL$) – świadczy o nagłej, dużej zmianie/szpilce.
    - **Reguła 2:** Dziewięć kolejnych punktów po tej samej stronie linii centralnej ($CL$) – świadczy o przesunięciu poziomu średniej procesu.
    - **Reguła 3:** Sześć kolejnych punktów stale rosnących lub stale malejących – świadczy o powolnym trendzie/dryfcie.
    - **Reguła 4:** Czternaście kolejnych punktów naprzemiennie rosnących i malejących – świadczy o periodycznych oscylacjach (np. cykle defrostów, wadliwy układ regulacji).

### REQ-03: Detekcja Trendów i Dryfu
*   Regresja liniowa wyliczająca współczynnik kierunkowy ($a$) w celu wykrycia powolnego wzrostu średniej temperatury (np. ubytek czynnika chłodniczego, zatykanie filtrów powietrza).

---

## 4. Raportowanie, Prezentacja UI i Alarmowanie (GxP Compliance)
*   **Szczegółowa Diagnostyka UI:** Użytkownik w widoku rewalidacji ma możliwość wejścia w zaawansowaną diagnostykę SPC każdego czujnika. System prezentuje tam interaktywne karty kontrolne oraz listuje wszystkie wykryte naruszenia reguł Nelsona. Komunikaty o naruszeniach muszą być kontrastowe i wyraźne (czerwone), a w przypadku ich braku wyświetlane jest zielone potwierdzenie stabilności.
*   **Raport PDF GxP:** Główny raport PDF z rewalidacji komory chłodniczej musi zawierać dedykowaną **Sekcję 4.3 (Weryfikacja Stabilności Procesu)** z tabelą statystyczną przedstawiającą wyznaczone granice i listę naruszeń dla każdego czujnika.
*   **Dynamiczne Wnioskowanie (Sekcja 4.2):** W przypadku wykrycia jakichkolwiek naruszeń reguł Nelsona na kartach Shewharta, system automatycznie umieszcza ostrzeżenie w podsumowaniu raportu sugerując wdrożenie działań korygujących (CAPA).
