package com.mac.bry.desktop.service.stats;

public class FftCalculator {

    public static double[] calculateFftSpectrum(double[] input) {
        if (input == null || input.length == 0) {
            return new double[0];
        }
        int n = input.length;
        // Zaokrąglenie do najbliższej potęgi 2 (Zero-padding)
        int m = 1;
        while (m < n) {
            m <<= 1;
        }
        
        double[] real = new double[m];
        double[] imag = new double[m];
        System.arraycopy(input, 0, real, 0, n); // Wypełnienie danymi wejściowymi, reszta to 0.0

        fft(real, imag);

        // Obliczenie widma amplitudy (moduł)
        // Wynik ma długość m / 2 ze względu na symetrię (Nyquist)
        double[] spectrum = new double[m / 2];
        for (int i = 0; i < m / 2; i++) {
            spectrum[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]) / n * 2.0;
            // Skalowanie amplitudy (indeks 0 to składowa stała - DC, którą dzielimy przez 2)
            if (i == 0) {
                spectrum[i] /= 2.0;
            }
        }
        return spectrum;
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n <= 1) return;

        // Bit-reversal permutation (Permutacja odwracania bitów)
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

        // Algorytm Cooley-Tukey (Decimation-in-time)
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
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
