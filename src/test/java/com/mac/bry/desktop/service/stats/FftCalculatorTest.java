package com.mac.bry.desktop.service.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FftCalculatorTest {

    @Test
    @DisplayName("should compute FFT and find correct dominant frequency")
    void shouldFindDominantFrequency() {
        // Generate a signal: 1 Hz sine wave, sampled at 8 Hz for 1 second (8 samples)
        // Sampling frequency Fs = 8 Hz. Signal frequency f = 1 Hz.
        // N = 8 is power of 2.
        int N = 8;
        double Fs = 8.0;
        double f = 1.0;
        double[] signal = new double[N];
        for (int i = 0; i < N; i++) {
            double t = i / Fs;
            signal[i] = Math.sin(2.0 * Math.PI * f * t);
        }

        double[] fftAmplitudes = FftCalculator.calculateFftSpectrum(signal);

        // FFT size will be padded to power of 2 (8 is already power of 2)
        // The frequency bin resolution is Fs / N = 8 / 8 = 1.0 Hz.
        // Bin 0: 0 Hz (DC)
        // Bin 1: 1 Hz
        // Bin 2: 2 Hz
        // Bin 3: 3 Hz
        // Bin 4: 4 Hz (Nyquist) - but note that the spectrum returned is length m/2 (4 bins: 0, 1, 2, 3)
        // Dominant frequency bin should be bin 1 (1 Hz)

        int maxBin = 0;
        double maxAmp = -1.0;
        // Search only up to N/2 (Nyquist limit)
        for (int i = 0; i < fftAmplitudes.length; i++) {
            if (fftAmplitudes[i] > maxAmp) {
                maxAmp = fftAmplitudes[i];
                maxBin = i;
            }
        }

        double dominantFrequency = maxBin * (Fs / (2.0 * fftAmplitudes.length));

        assertThat(dominantFrequency).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("should pad input data to next power of two")
    void shouldPadInputToPowerOfTwo() {
        // 10 samples (not a power of 2). Next power of 2 is 16.
        double[] signal = new double[10];
        double[] fftAmplitudes = FftCalculator.calculateFftSpectrum(signal);

        // Since it's padded to 16, and spectrum length is m / 2 = 8
        assertThat(fftAmplitudes).hasSize(8);
    }
}
