# Tabele Parametrów Obliczeniowych - Moduł Statystyczny

Poniższe tabele podsumowują parametry obliczane w ramach każdego z czterech modułów statystycznych dla walidacji temperaturowej.

---

### 🌡️ 1. Statystyka Opisowa i Przestrzenna (Descriptive & Spatial Stats)

| Parametr | Metoda / Wzór matematyczny | Dane wejściowe | Zastosowanie w GxP / Interpretacja |
| :--- | :--- | :--- | :--- |
| **Minimum (AbsMin)** | $T_{min} = \min(x_i)$ | Surowe dane z 1 czujnika | Najzimniejszy zarejestrowany punkt w czasie dla danej lokalizacji. |
| **Maksimum (AbsMax)** | $T_{max} = \max(x_i)$ | Surowe dane z 1 czujnika | Najcieplejszy zarejestrowany punkt w czasie dla danej lokalizacji. |
| **Średnia ($\mu$)** | $\bar{x} = \frac{1}{n}\sum_{i=1}^{n} x_i$ | Surowe dane z 1 czujnika | Średnia temperatura robocza czujnika. |
| **Mediana** | Wartość środkowa posortowanego zbioru | Surowe dane z 1 czujnika | Odporna na pojedyncze piki pomiarowe (szum) średnia wartość. |
| **Odchylenie standardowe ($s$)** | $s = \sqrt{\frac{1}{n-1}\sum_{i=1}^{n}(x_i - \bar{x})^2}$ | Surowe dane z 1 czujnika | Stabilność temperatury w czasie (im mniejsze, tym stabilniejszy czujnik). |
| **Współczynnik zmienności (RSD / CV)** | $RSD = \frac{s}{\bar{x}} \times 100\%$ | Średnia i odchylenie standardowe | Względna zmienność (poniżej 5% świadczy o stabilności termicznej). |
| **Skośność (Skewness)** | $\gamma = \frac{\frac{1}{n}\sum(x_i - \bar{x})^3}{s^3}$ | Surowe dane z 1 czujnika | Kierunek odchyleń (dodatnia: częstsze piki ciepła, ujemna: chłodu). |
| **Kurtoza (Kurtosis)** | $\kappa = \frac{\frac{1}{n}\sum(x_i - \bar{x})^4}{s^4} - 3$ | Surowe dane z 1 czujnika | Skupienie wokół średniej (wysoka kurtoza = małe wahania o dużym skupieniu). |
| **Chwilowy rozstęp przestrzenny ($\Delta T_t$)** | $\Delta T_t = T_{max, t} - T_{min, t}$ | Odczyty wszystkich czujników w chwili $t$ | Jednorodność temperatury w komorze w danym momencie. |
| **Maksymalny rozstęp przestrzenny** | $\Delta T_{max} = \max(\Delta T_t)$ | Wektor rozstępów przestrzennych | Najgorszy przypadek (worst-case) różnicy temperatur w komorze. |

---

### 🧪 2. Testowanie Hipotez Statystycznych (Hypothesis Testing)

| Parametr / Test | Statystyka testowa | Cel matematyczny | Kontekst Walidacji GxP |
| :--- | :--- | :--- | :--- |
| **Test Shapiro-Wilka ($W$, $p$-value)** | $W = \frac{(\sum a_i x_{(i)})^2}{\sum(x_i - \bar{x})^2}$ | Badanie normalności rozkładu | Kwalifikacja danych przed wykonaniem testów ANOVA lub t-Studenta. |
| **Jednoczynnikowa ANOVA ($F$, $p$-value)** | $F = \frac{\text{wariancja międzygrupowa}}{\text{wariancja wewnątrzgrupowa}}$ | Porównanie średnich wielu grup | Dowód, czy różnice temperatur między półkami/strefami są istotne. |
| **Test Kruskala-Wallisa ($H$, $p$-value)** | Test rangowy (nieparametryczny) | Porównanie wielu grup bez rozkładu normalnego | Alternatywa dla ANOVA dla danych z anomaliami (np. podczas defrostu). |
| **Test F-Snedecora ($F$, $p$-value)** | $F = \frac{s_1^2}{s_2^2}$ | Porównanie wariancji dwóch stref | Dowód na równoważność stabilności (np. strefa drzwi vs. środek komory). |
| **Test Równoważności TOST ($t_1, t_2$, $p$-value)** | $t = \frac{\bar{x}_1 - \bar{x}_2 \mp \theta}{SE}$ | Udowodnienie braku różnic w granicach $\pm\theta$ | **Naukowy dowód na równorzędność stref** (np. po rekonfiguracji komory). |

---

### 📈 3. Statystyczna Kontrola Procesu (SPC) i Trendy (SPC & Trends)

| Parametr | Wzór matematyczny | Dane wejściowe | Zastosowanie w GxP / Interpretacja |
| :--- | :--- | :--- | :--- |
| **Wskaźnik Zdolności $C_p$** | $C_p = \frac{USL - LSL}{6\sigma}$ | Odchylenie standardowe i limity | Potencjalna zdolność komory do utrzymania limitów (np. $2-8^\circ\text{C}$). |
| **Wskaźnik Zdolności $C_{pk}$** | $C_{pk} = \min\left(\frac{USL - \mu}{3\sigma}, \frac{\mu - LSL}{3\sigma}\right)$ | Średnia, odchylenie, limity | Rzeczywista zdolność komory ($C_{pk} \ge 1.33$ = proces w pełni stabilny). |
| **UCL / LCL (Karty Shewharta)** | $UCL/LCL = \bar{\bar{x}} \pm 3\bar{s}$ | Średnie i odchylenia podgrup | Granice kontrolne (wykrywanie powolnego rozregulowania chłodzenia). |
| **Współczynnik regresji ($a$)** | $a = \frac{\sum(t_i - \bar{t})(x_i - \bar{x})}{\sum(t_i - \bar{t})^2}$ | Temperatura w czasie | Wykrywanie dryfu termicznego (np. spadek wydajności izolacji ścian). |
| **Współczynnik $R^2$** | $R^2 = 1 - \frac{\text{Suma kwadratów reszt}}{\text{Suma kwadratów całkowita}}$ | Dopasowanie linii regresji | Ocena, czy trend dryfu jest stały i przewidywalny. |

---

### 🌀 4. Analiza Szeregów Czasowych i Cykliczności (Time Series & Cycles)

| Parametr | Metoda / Wzór matematyczny | Dane wejściowe | Zastosowanie w GxP / Interpretacja |
| :--- | :--- | :--- | :--- |
| **Częstotliwość dominująca ($f_{dom}$)** | $f_{dom} = \text{argmax}(\|X(f)\|)$ z FFT | Spektrum FFT sygnału | Identyfikacja okresu cyklu sprężarki lub grzałek (histereza PID). |
| **Amplituda drgań ($A$)** | $A = 2 \cdot \|X(f_{dom})\|$ | Spektrum FFT sygnału | Rzeczywisty zakres wahań temperatury wywołany cyklem pracy. |
| **Amplituda cyklu defrostu** | $T_{max, defrost} - T_{baseline}$ | Detekcja pików temperatury | Ocena wpływu odszraniania na wzrost temperatury w komorze. |
| **Czas trwania defrostu ($t_{defrost}$)** | Czas przebywania powyżej progu $\frac{dT}{dt}$ | Różniczkowanie sygnału | Weryfikacja zgodności z dopuszczalnymikiem czasu zaburzenia (np. < 15 min). |
| **Szybkość zmian temperatury ($\frac{dT}{dt}$)** | $\frac{x_t - x_{t-1}}{\Delta t}$ | Dane czasowe czujnika | Monitorowanie szybkości nagrzewania komory przy awarii (Hold-over time). |
| **Autokorelacja w czasie ($R(\tau)$)** | $R(\tau) = \frac{E[(x_t - \mu)(x_{t+\tau} - \mu)]}{\sigma^2}$ | Dane przesunięte o czas $\tau$ | Ocena bezwładności cieplnej komory i weryfikacja częstości pomiarów. |
