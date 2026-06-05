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

### REQ-02: Karta Kontrolna Shewharta (X-bar / S)
*   Wizualizacja średnich podgrup czasowych (X-bar) oraz ich odchyleń standardowych (S) na wykresie kontrolnym.
*   Automatyczne wyliczanie Linii Centralnej (CL) oraz Górnej i Dolnej Granicy Kontrolnej (UCL, LCL) jako $\pm 3\sigma$.
*   **Detekcja OOC (Out of Control):** Oznaczanie punktów i trendów naruszających reguły stabilności (np. nagłe przesunięcie średniej, pojedynczy punkt poza granicami $3\sigma$).

### REQ-03: Detekcja Trendów i Dryfu
*   Regresja liniowa wyliczająca współczynnik kierunkowy ($a$) w celu wykrycia powolnego wzrostu średniej temperatury (np. zatykanie filtrów powietrza w magazynie).

## 4. Raportowanie i Alarmowanie (Alarming)
*   System generuje raport z kartami SPC w formacie PDF jako załącznik do raportu z mapowania.
*   W przypadku $C_{pk} < 1.0$ system generuje krytyczne ostrzeżenie sugerujące podjęcie działań korygujących (CAPA).
