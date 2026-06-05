# Plan Implementacji - Analiza Szeregów Czasowych i Cykliczności

Plan wdrożenia natywnych algorytmów analizy szeregów czasowych, detekcji cykli oraz Szybkiej Transformaty Fouriera (FFT) dla aplikacji walidacyjnej.

## 1. Wybór Algorytmów
W celu spełnienia wymogów braku zewnętrznych zależności oraz łatwości walidacji kodu (CSV), implementujemy własny algorytm **FFT (Cooley-Tukey)** oraz prosty detektor pików oparty na ruchomym oknie różnicowym (Peak Detection).

## 2. Architektura i Klasy

### [NEW] [FftCalculator.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/FftCalculator.java)
Klasa wykonująca przekształcenie FFT na tablicy danych (długość wejściowa musi być potęgą 2 - dane zostaną dopełnione zerami (zero-padding) w razie potrzeby).

```java
package com.mac.bry.desktop.service.stats;

public class FftCalculator {

    public static double[] calculateFftSpectrum(double[] input) {
        int n = input.length;
        // Zaokrąglenie do najbliższej potęgi 2
        int m = 1;
        while (m < n) {
            m <<= 1;
        }
        
        double[] real = new double[m];
        double[] imag = new double[m];
        System.arraycopy(input, 0, real, 0, n); // Zera na końcu (padding)

        fft(real, imag);

        // Obliczenie modułu widma (amplituda)
        double[] spectrum = new double[m / 2];
        for (int i = 0; i < m / 2; i++) {
            spectrum[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return spectrum;
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n <= 1) return;

        // Bit-reversal permutation
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                double tempReal = real[i];
                real[i] = real[j];
                real[j] = tempReal;
                double tempImag = imag[i];
                imag[i] = imag[j];
                imag[j] = tempImag;
            }
            int m = n >> 1;
            while (m >= 1 && j >= m) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }

        // Cooley-Tukey decimation-in-time
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wlenReal = Math.cos(angle);
            double wlenImag = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double wReal = 1.0;
                double wImag = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int idx1 = i + k;
                    int idx2 = i + k + len / 2;
                    double uReal = real[idx1];
                    double uImag = imag[idx1];
                    double tReal = real[idx2] * wReal - imag[idx2] * wImag;
                    double tImag = real[idx2] * wImag + imag[idx2] * wReal;
                    
                    real[idx1] = uReal + tReal;
                    imag[idx1] = uImag + tImag;
                    real[idx2] = uReal - tReal;
                    imag[idx2] = uImag - tImag;
                    
                    double nextWReal = wReal * wlenReal - wImag * wlenImag;
                    wImag = wReal * wlenImag + wImag * wlenReal;
                    wReal = nextWReal;
                }
            }
        }
    }
}
```

### [NEW] [DefrostCycleDetector.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/DefrostCycleDetector.java)
Klasa wyszukująca nagłe, strome wzrosty temperatury połączone z przekroczeniem określonego progu amplitudy i czasu trwania:
```java
public class DefrostCycleDetector {
    public List<DefrostCycle> detectCycles(double[] values, double timeStepMinutes, double rateThreshold, double amplitudeThreshold) {
        List<DefrostCycle> cycles = new ArrayList<>();
        // Logika przeszukiwania gradientu dT/dt
        // ...
        return cycles;
    }
}
```

## 3. Integracja z Raportem
W module generowania PDF (JasperReports lub OpenPDF) dodana zostanie sekcja **"Analiza Cykli Odszraniania"**, pokazująca wykryte incydenty defrostu, ich czas startu, czas trwania oraz maksymalną zarejestrowaną temperaturę.

## 4. Plan Testów (Verification Plan)
*   **Testy jednostkowe (`FftCalculatorTest`):**
    *   Weryfikacja widma dla czystej sinusoidy o znanej częstotliwości (pik powinien pojawić się na indeksie odpowiadającym tej częstotliwości).
    *   Weryfikacja obsługi dopełniania zerami (dla tablic o rozmiarach innych niż potęga 2).
*   **Testy jednostkowe (`DefrostCycleDetectorTest`):**
    *   Test na sztucznie wygenerowanym szeregu czasowym z 3 dodanymi "szpilkami" reprezentującymi cykle defrostu.
