# Analiza Biznesowa (BA) - Moduł Statystyki Opisowej i Przestrzennej

## 1. Cel Biznesowy
Celem jest dostarczenie zaawansowanych wskaźników statystycznych opisujących rozkład temperatur w przestrzeni oraz czasie. Pozwala to na naukowe udowodnienie jednorodności środowiska przechowywania (np. komora zimna, magazyn) zgodnie z wymaganiami GxP.

## 2. Kontekst Regulacyjny (FDA, WHO, ISPE)
Zgodnie z wytycznymi:
*   **WHO Technical Report Series 961, Annex 9, Supplement 8 (2015):** Temperature mapping of storage areas.
*   **ISPE Good Practice Guide: Controlled Temperature Chambers, Version 2.0 (2021):** Wytyczne w zakresie kwalifikacji komór kontrolowanych.
*   **USP <1079.4> Temperature Mapping for the Qualification of Storage Areas:** Oficjalny standard od 1 maja 2024 roku.
*   **USP <1079.2> Mean Kinetic Temperature in the Evaluation of Temperature Excursions.**

Kluczowe wytyczne regulacyjne:
*   Samo wyznaczenie punktów Hotspot/Coldspot nie jest wystarczające do pełnej kwalifikacji urządzenia.
*   Należy ocenić zmienność przestrzenną (Spatial Uniformity) w celu zidentyfikowania martwych stref wentylacyjnych.
*   Zmienność czasowa poszczególnych punktów pomiarowych musi być stale monitorowana i podsumowana za pomocą odchylenia standardowego oraz współczynnika zmienności (CV/RSD).

## 3. Wymagania Funkcjonalne

### REQ-01: Podstawowa Statystyka Opisowa dla Czujników
Dla każdego czujnika zaangażowanego w sesję mapowania system musi wyliczyć:
*   **Wartości skrajne:** Minimum (AbsMin) i Maksimum (AbsMax).
*   **Średnią arytmetyczną ($\mu$):** Odniesienie do średnich warunków pracy.
*   **Medianę:** Do oceny odporności na zakłócenia/piki.
*   **Odchylenie Standardowe ($\sigma$):** Wskaźnik stabilności czasowej pojedynczego sensora.
*   **Współczynnik Zmienności (RSD / CV):** Definiowany jako $RSD = (\sigma / \mu) \times 100\%$. Wartość ta pozwala na bezpośrednie porównanie stabilności czujników pracujących w różnych temperaturach średnich.

### REQ-02: Zaawansowane Statystyki Rozkładu (Skośność i Kurtoza)
*   **Skośność (Skewness):** Weryfikacja asymetrii rozkładu (czy system częściej dryfuje w stronę ciepłą, czy zimną).
*   **Kurtoza (Kurtosis):** Ocena "spłaszczenia" rozkładu (czy temperatura oscyluje gwałtownie wokół średniej, czy rozrzut jest stabilny).

### REQ-03: Analiza Jednorodności Przestrzennej (Spatial Uniformity)
*   **Rozstęp Przestrzenny w czasie (Spatial Range):** Wyliczanie różnicy $\Delta T_t = T_{max}(t) - T_{min}(t)$ dla każdego znacznika czasu $t$ ze wszystkich logerów. Średni i maksymalny rozstęp przestrzenny są kluczowymi wskaźnikami wydajności układu HVAC.
    *Uwaga terminologiczna:* W statystyce "range" oznacza różnicę max - min, natomiast "spread" jest pojęciem szerszym (obejmującym $\sigma$, IQR, MAD). W tym dokumencie i w kodzie konsekwentnie stosujemy **Spatial Range**, aby uniknąć dwuznaczności.
*   **Analiza Poziomów Fizycznych (Góra, Środek, Dół):** Agregacja statystyk dla czujników umieszczonych na tej samej wysokości w celu identyfikacji pionowego gradientu temperatur.
    Wymagane wskaźniki na każdym poziomie fizycznym:
    *   Średnia ($\bar{x}$) z 95% przedziałem ufności (CI), wyliczonym jako $\bar{x} \pm t(\alpha/2, n-1) \cdot s/\sqrt{n}$.
    *   Odchylenie standardowe ($s$).
    *   Zakres [min, max].
    
    Metodologia weryfikacji istotności różnic między poziomami:
    *   Stosuje się test **Welch ANOVA** (który nie zakłada równości wariancji między poziomami) z poziomem istotności $\alpha = 0.05$.
    *   W przypadku odrzucenia hipotezy zerowej $H_0$ (istotne różnice między poziomami) przeprowadza się test post-hoc **Games-Howell** dla par poziomów.
    *   W przypadku, gdy rozkład danych istotnie odbiega od normalnego, stosuje się nieparametryczną alternatywę: test **Kruskal-Wallis** oraz test post-hoc **Dunn**.
    
    Raport walidacyjny musi zawierać:
    *   Wartość pionowego gradientu temperatur $\Delta T_{vert} = |\bar{x}_{gora} - \bar{x}_{dol}|$.
    *   Wartość statystyki testowej oraz p-value.
    *   Werdykt walidacyjny (gradient akceptowalny / wymagane CAPA).

## 4. Kryteria Akceptacji (GxP)
*   **AC-01a (RSD - temperatury dodatnie):** Dla zakresów temperatur dodatnich ($\ge 0^\circ\text{C}$) współczynnik zmienności (RSD) dla każdego sensora w fazie stabilizacji komory nie może przekraczać 5.0%.
*   **AC-01b (Bezwzględne odchylenie standardowe):** Dla cold chain stosujemy odchylenie standardowe $\sigma$ w $^\circ\text{C}$ jako główny wskaźnik stabilności czasowej zgodnie z zaleceniami WHO TRS 961 Supplement 8. Progi akceptacji:
    *   Lodówki ($2 - 8^\circ\text{C}$): $\sigma \le 0.3^\circ\text{C}$
    *   Magazyny / Kontrolowana temperatura pokojowa ($15 - 25^\circ\text{C}$): $\sigma \le 0.5^\circ\text{C}$
    *   Zamrażarki ($-25^\circ\text{C}$): $\sigma \le 1.0^\circ\text{C}$
    *   Zamrażarki głębokiego mrożenia ($-80^\circ\text{C}$): $\sigma \le 2.0^\circ\text{C}$
    *Ostateczne progi mogą być dostosowane w protokole walidacyjnym danego urządzenia.*
*   **AC-01c (Zakaz RSD dla zakresów ujemnych):** Współczynnik zmienności RSD nie może być stosowany dla temperatur ujemnych (zamrażarki), ponieważ wartość średnia w mianowniku zbliża się do zera lub jest ujemna, co powoduje niestabilność matematyczną wskaźnika i pozbawia go sensownej interpretacji.
*   **AC-02:** Maksymalny chwilowy rozstęp przestrzenny ($\Delta T_t$) w komorze kontrolowanej nie może przekroczyć limitu zdefiniowanego w protokole walidacyjnym (np. $2.0^\circ\text{C}$ dla komór $2.0 - 8.0^\circ\text{C}$).
