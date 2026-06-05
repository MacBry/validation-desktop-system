# Analiza Biznesowa (BA) - Moduł Statystyki Opisowej i Przestrzennej

## 1. Cel Biznesowy
Celem jest dostarczenie zaawansowanych wskaźników statystycznych opisujących rozkład temperatur i wilgotności w przestrzeni oraz czasie. Pozwala to na naukowe udowodnienie jednorodności środowiska przechowywania (np. komora zimna, magazyn) zgodnie z wymaganiami GxP.

## 2. Kontekst Regulacyjny (FDA, WHO, ISPE)
Zgodnie z wytycznymi **WHO Technical Report Series 961 (Appendix 9)** oraz **ISPE Cold Chain Management**:
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
*   **Rozstęp Przestrzenny w czasie (Spatial Spread):** Wyliczanie różnicy $\Delta T = T_{max} - T_{min}$ dla każdego znacznika czasu (timestamp) ze wszystkich logerów. Średni i maksymalny rozstęp przestrzenny są kluczowymi wskaźnikami wydajności układu HVAC.
*   **Analiza Poziomów Fizycznych (Góra, Środek, Dół):** Agregacja statystyk dla czujników umieszczonych na tej samej wysokości w celu identyfikacji pionowego gradientu temperatur.

## 4. Kryteria Akceptacji (GxP)
*   **AC-01:** Współczynnik zmienności (RSD) dla każdego sensora w fazie stabilizacji komory nie może przekraczać 5.0%.
*   **AC-02:** Maksymalny chwilowy rozstęp przestrzenny ($\Delta T$) w komorze kontrolowanej nie może przekroczyć limitu zdefiniowanego w protokole walidacyjnym (np. $2.0^\circ\text{C}$ dla komór $2.0 - 8.0^\circ\text{C}$).
