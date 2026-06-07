package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class StatisticsPerformanceBenchmarkTest {

    @Test
    @DisplayName("PTS-01: Profiling DefrostCycleDetector with 10k points")
    void profileDefrostCycleDetector() {
        // 1. Przygotowanie danych testowych (N = 10,000)
        int size = 10000;
        List<ThermoMeasurementPoint> points = new ArrayList<>(size);
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 7, 12, 0, 0);

        // Stabilne 5.0 °C z szumem, a co 500 pomiarów sztuczny pik defrostu
        for (int i = 0; i < size; i++) {
            double temp = 5.0 + 0.05 * Math.sin(i);
            if (i % 500 == 0 && i > 0) {
                temp = 9.0; // pik defrostu
            }
            points.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .timestampLocal(baseTime.plusMinutes(i))
                    .rawCelsius(temp)
                    .build());
        }

        // 2. Faza Rozgrzania (JIT Warmup)
        for (int i = 0; i < 15; i++) {
            DefrostCycleDetector.detectCycles(points, "WarmupSensor", 0.2, 2.0);
        }

        // 3. Faza Pomiaru Właściwego
        int iterations = 100;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DefrostCycleDetector.detectCycles(points, "BenchmarkSensor", 0.2, 2.0);
        }
        long duration = System.nanoTime() - start;
        double avgMillis = (double) duration / iterations / 1_000_000.0;

        System.out.println("=== DEFROST CYCLE DETECTOR PERFORMANCE ===");
        System.out.printf("Dataset size: %d points\n", size);
        System.out.printf("Average execution time: %.3f ms\n", avgMillis);
        System.out.println("==========================================");

        // Asercja: Średni czas powinien być poniżej 75 ms na współczesnych maszynach JVM
        assertThat(avgMillis).isLessThan(75.0);
    }

    @Test
    @DisplayName("PTS-02: FFT Cooley-Tukey Benchmark with 1M samples")
    void benchmarkFftOneMillionSamples() {
        // 1. Przygotowanie tablicy 2^20 (1 048 576 próbek)
        int powerOf2Size = 1 << 20; // 1,048,576
        double[] dataset = new double[powerOf2Size];
        Random rand = new Random(42);

        // Generowanie sygnału z sumą sinusoid i szumem
        for (int i = 0; i < powerOf2Size; i++) {
            dataset[i] = 5.0 
                    + 2.0 * Math.sin(2.0 * Math.PI * i / 100.0) 
                    + 0.5 * Math.cos(2.0 * Math.PI * i / 25.0) 
                    + rand.nextGaussian() * 0.1;
        }

        // 2. Faza Rozgrzania (JIT Warmup) - 5 powtórzeń
        for (int i = 0; i < 5; i++) {
            FftCalculator.calculateFftSpectrum(dataset);
        }

        // 3. Faza Pomiaru Właściwego - 5 powtórzeń
        int iterations = 5;
        double totalMillis = 0.0;
        double minMillis = Double.MAX_VALUE;
        double maxMillis = Double.MIN_VALUE;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            FftCalculator.calculateFftSpectrum(dataset);
            long duration = System.nanoTime() - start;
            double elapsedMillis = (double) duration / 1_000_000.0;

            totalMillis += elapsedMillis;
            if (elapsedMillis < minMillis) minMillis = elapsedMillis;
            if (elapsedMillis > maxMillis) maxMillis = elapsedMillis;
        }

        double avgMillis = totalMillis / iterations;

        System.out.println("=== FFT COOLEY-TUKEY BENCHMARK ===");
        System.out.printf("Dataset size: %d samples (2^20)\n", powerOf2Size);
        System.out.printf("Min execution time: %.2f ms\n", minMillis);
        System.out.printf("Max execution time: %.2f ms\n", maxMillis);
        System.out.printf("Average execution time: %.2f ms\n", avgMillis);
        System.out.println("==================================");

        // Asercja: Średni czas powinien być poniżej 800 ms (z dużym marginesem dla słabszych środowisk CI)
        assertThat(avgMillis).isLessThan(800.0);
    }
}
