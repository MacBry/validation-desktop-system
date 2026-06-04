# Business Analysis (BA): Logika GxP i Analiza Pomiary Transportowych (Jednostka 2)

## 1. Cel Biznesowy
Podstawowym zadaniem tej jednostki jest automatyzacja oceny metrologicznej transportu według wytycznych GxP. System musi:
1. Zweryfikować, czy temperatura na trasie spełniała kryteria akceptacji (brak przekroczeń).
2. Umożliwić „kadrowanie” pomiarów (Trim Range), aby wykluczyć odczyty wykonane przed fizycznym rozpoczęciem podróży i po jej zakończeniu.
3. Wyznaczyć czas podtrzymania temperatury (Hold-Time) w przypadku zasymulowanej awarii zasilania (odłączenia komory przenośnej).

Dzięki temu eliminujemy ryzyko odrzucenia walidacji z powodu „szumów” temperaturowych na stacji załadunku/rozładunku.

---

## 2. Dedykowane Kryteria Akceptacji GxP

W zależności od typu transportowanego materiału i przeznaczenia urządzenia transportowego, system musi automatycznie zaaplikować odpowiednie reguły (Tabela 1 SOP):

| Lp. | Typ Transportowanego Składnika / Próby | Min. Temp [°C] | Max. Temp [°C] | Dodatkowe Wymogi w SOP |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **KPK do produkcji KKP / osocza z aferezy** | 20.0 | 24.0 | Pomiar co 10 minut. |
| 2 | **KPK bez prod. KKP / KKCz do szpitali** | 2.0 | 10.0 | Pomiar co 10 minut. |
| 3 | **FFP do podmiotów leczniczych (szpitali)** | — | -18.0 | Pomiar co 10 minut. |
| 4 | **FFP transport wewnętrzny w RCKiK** | — | -20.0 | Pomiar co 1 minutę, 3 cykle pomiarowe. |
| 5 | **Standardowe próby laboratoryjne** | 2.0 | 25.0 | Pomiar co 10 minut. |
| 6 | **Zamrożone próby laboratoryjne** | — | -20.0 | Pomiar co 10 minut. |
| 7 | **Próby w suchym lodzie** | — | -25.0 | Pomiar co 30 minut, dokładnie 145 pomiarów (72h). |

---

## 3. Szczegółowe Wymagania Funkcjonalne

### 3.1. Kadrowanie Wykresu (Trim Range)
Wbudowany w rejestrator sensor zbiera dane od momentu zaprogramowania (w biurze) do momentu odczytu. Zgodnie z **SOP Pkt 90** pomiar zaczyna się dopiero po zamknięciu komory/kontenera.
* **Wymaganie:** System musi pozwolić operatorowi na zaznaczenie punktu startowego ($T_{\text{start}}$) i końcowego ($T_{\text{stop}}$) na wykresie.
* **Wynik:** Do kalkulacji średnich, minimów, maksimów oraz alarmów GxP brane są pod uwagę wyłącznie próbki czasowe mieszczące się w przedziale $[T_{\text{start}}, T_{\text{stop}}]$. Dane poza tym zakresem są odrzucane (ignorowane w raporcie).

### 3.2. Wyznaczanie Czasu Podtrzymania (Hold-Time)
Wykonywane podczas testu awarii zasilania przenośnych komór (SOP Pkt 122).
* **Definicja Hold-Time:** Czas (w minutach) liczony od momentu odłączenia zasilania komory chłodzącej ($T_{\text{awaria}}$) do momentu, w którym temperatura po raz pierwszy przekroczyła dopuszczalną granicę (np. wzrosła powyżej $-18^\circ\text{C}$ dla FFP).
* **Wymaganie:** System automatycznie identyfikuje $T_{\text{awaria}}$ (wskazany przez operatora na wykresie) i wylicza czas bezpiecznego podtrzymania, podając go w raporcie.
