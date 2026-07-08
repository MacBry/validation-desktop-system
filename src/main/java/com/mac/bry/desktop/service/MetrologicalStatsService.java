package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.stats.CorrectedStatsDTO;
import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.service.stats.SpcEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za zaawansowane obliczenia statystyczne i metrologiczne
 * (zgodność z GxP, FDA 21 CFR Part 11 oraz budżet niepewności GUM).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetrologicalStatsService {

    private final CalibrationCorrectionService calibrationCorrectionService;

    private static final double GAS_CONSTANT = 8.314472; // J/(mol*K)
    private static final double DEFAULT_ACTIVATION_ENERGY = 83.14; // USP <1150> default: 83.14 kJ/mol

    /**
     * Główna metoda wyliczająca komplet statystyk dla serii pomiarowej.
     * Uzupełnia wszystkie pola statystyczne w obiekcie ThermoMeasurementSeries.
     */
    public void calculateStatistics(ThermoMeasurementSeries series) {
        if (series == null) return;
        List<ThermoMeasurementPoint> points = series.getMeasurements();
        if (points == null || points.isEmpty()) {
            log.warn("Brak punktów pomiarowych dla serii ID: {}. Pomijam statystyki.", series.getId());
            return;
        }

        int n = points.size();
        series.setMeasurementsCount(n);

        // Wyznaczenie podstawowych sum do średniej
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        // Limity czasowe z komory chłodniczej (efektywne pod kątem przechowywanego produktu)
        Double minLimit = null;
        Double maxLimit = null;
        CoolingChamber chamber = series.getCoolingChamber();
        if (chamber != null) {
            minLimit = chamber.getEffectiveMinTempLimit();
            maxLimit = chamber.getEffectiveMaxTempLimit();
        }

        // Energia aktywacji (MKT Arrheniusa)
        double activationEnergy = DEFAULT_ACTIVATION_ENERGY;
        if (chamber != null && chamber.getMaterialType() != null) {
            BigDecimal ae = chamber.getMaterialType().getActivationEnergy();
            if (ae != null) {
                activationEnergy = ae.doubleValue();
            }
        }
        double deltaH = activationEnergy * 1000.0; // Zamiana na J/mol
        double mktSum = 0.0;

        // Inicjalizacja liczników przekroczeń
        long timeInMinutes = 0;
        long timeOutMinutes = 0;
        int violations = 0;
        long maxViolationDuration = 0;
        long currentViolationDuration = 0;
        boolean inViolation = false;

        // Pętla po wszystkich punktach pomiarowych
        for (int i = 0; i < n; i++) {
            ThermoMeasurementPoint point = points.get(i);
            double temp = point.getRawCelsius();
            sum += temp;

            if (temp < min) min = temp;
            if (temp > max) max = temp;

            // MKT: e^(-deltaH / (R * T_kelvin))
            double tempKelvin = temp + 273.15;
            mktSum += Math.exp(-deltaH / (GAS_CONSTANT * tempKelvin));

            // Statystyki czasowe naruszeń (bazujące na interwale)
            if (minLimit != null && maxLimit != null && i < n - 1) {
                long duration = Duration.between(
                        point.getTimestampLocal(),
                        points.get(i + 1).getTimestampLocal()).toMinutes();

                boolean isOut = temp < minLimit || temp > maxLimit;

                if (isOut) {
                    timeOutMinutes += duration;
                    currentViolationDuration += duration;
                    if (!inViolation) {
                        violations++;
                        inViolation = true;
                    }
                } else {
                    timeInMinutes += duration;
                    if (inViolation) {
                        if (currentViolationDuration > maxViolationDuration) {
                            maxViolationDuration = currentViolationDuration;
                        }
                        currentViolationDuration = 0;
                        inViolation = false;
                    }
                }
            }
        }

        // Finalizacja ostatniego naruszenia jeśli trwa do końca serii
        if (inViolation && currentViolationDuration > maxViolationDuration) {
            maxViolationDuration = currentViolationDuration;
        }

        // Zapisz podstawowe statystyki
        series.setMinTemperature(min);
        series.setMaxTemperature(max);
        series.setAvgTemperature(sum / n);

        // MKT = (deltaH / R) / -ln(mktSum / n) - 273.15
        if (mktSum > 0) {
            double mktValue = (deltaH / GAS_CONSTANT) / (-Math.log(mktSum / n)) - 273.15;
            series.setMktTemperature(mktValue);
        }

        // Odchylenie standardowe i wariancja (próbkowa, korekcja Bessela: n-1)
        // Spójne z SensorStatsEngine.calculateVariance() oraz oznaczeniem 's' w artykule.
        double varianceSum = 0.0;
        for (ThermoMeasurementPoint point : points) {
            double diff = point.getRawCelsius() - series.getAvgTemperature();
            varianceSum += diff * diff;
        }
        double var = n > 1 ? varianceSum / (n - 1) : 0.0;
        series.setVariance(var);
        double stdDev = Math.sqrt(var);
        series.setStdDeviation(stdDev);

        // Współczynnik zmienności CV% = (s / x̄) × 100%
        // RSD jest matematycznie niestabilny dla średnich ujemnych lub bliskich zeru.
        // Zgodnie z WHO TRS 961 Annex 9 Supplement 8: dla temp. ujemnych (zamrażarki)
        // należy oceniać wyłącznie bezwzględne odchylenie standardowe (σ w °C),
        // a nie RSD. Pole cvPercentage ustawiamy na null jako sygnał "nie dotyczy".
        double avgTemp = series.getAvgTemperature();
        if (avgTemp > 0.0) {
            series.setCvPercentage((stdDev / avgTemp) * 100.0);
        } else {
            series.setCvPercentage(null); // N/A – użyj stdDev jako kryterium oceny
        }

        // Mediana i percentyle (potrzebują posortowanej listy temperatur)
        List<Double> sortedTemps = points.stream()
                .map(ThermoMeasurementPoint::getRawCelsius)
                .sorted()
                .collect(Collectors.toList());

        series.setMedianTemperature(calculateMedian(sortedTemps));

        if (n >= 2) {
            series.setPercentile5(calculatePercentile(sortedTemps, 5.0));
            series.setPercentile95(calculatePercentile(sortedTemps, 95.0));
        } else {
            series.setPercentile5(min);
            series.setPercentile95(max);
        }

        // Zapisz statystyki czasowe naruszeń
        series.setTotalTimeInRangeMinutes(timeInMinutes);
        series.setTotalTimeOutOfRangeMinutes(timeOutMinutes);
        series.setViolationCount(violations);
        series.setMaxViolationDurationMinutes(maxViolationDuration);

        // --- Regresja liniowa dla dryftu ---
        // T(t) = a + b * t (t = czas w godzinach od początku serii)
        double sumT = 0.0;
        double sumTime = 0.0;
        double sumTimeT = 0.0;
        double sumTimeSq = 0.0;
        LocalDateTime start = points.get(0).getTimestampLocal();

        for (ThermoMeasurementPoint p : points) {
            double tHours = ChronoUnit.SECONDS.between(start, p.getTimestampLocal()) / 3600.0;
            double temp = p.getRawCelsius();
            sumT += temp;
            sumTime += tHours;
            sumTimeT += tHours * temp;
            sumTimeSq += tHours * tHours;
        }

        double denominator = (n * sumTimeSq - sumTime * sumTime);
        double bOrig = 0.0;
        if (Math.abs(denominator) > 1e-9) {
            bOrig = (n * sumTimeT - sumTime * sumT) / denominator;
            series.setTrendCoefficient(bOrig);
        } else {
            series.setTrendCoefficient(0.0);
        }

        // --- Analiza Drift vs Spike (Metoda A+) ---
        if (n > 5) {
            double aOrig = (sumT - bOrig * sumTime) / n;

            // 1. Oblicz residuale (odchylenia od linii trendu)
            List<Double> residuals = new ArrayList<>();
            for (ThermoMeasurementPoint p : points) {
                double tHours = ChronoUnit.SECONDS.between(start, p.getTimestampLocal()) / 3600.0;
                residuals.add(p.getRawCelsius() - (aOrig + bOrig * tHours));
            }

            // 2. Metoda MAD (Median Absolute Deviation)
            double medianRes = calculateMedian(residuals);
            List<Double> absDeviations = new ArrayList<>();
            for (double r : residuals) {
                absDeviations.add(Math.abs(r - medianRes));
            }
            double mad = calculateMedian(absDeviations);
            if (mad < 0.01) {
                mad = 0.01;
            }

            // 3. Wykrywanie szpilek (Próg 3.5 * MAD)
            double threshold = 3.5 * mad;
            List<Integer> spikeIndices = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (absDeviations.get(i) > threshold) {
                    spikeIndices.add(i);
                }
            }

            // 3b. Event padding ±1 (rozszerzenie strefy spike'a o sąsiadów)
            Set<Integer> paddedSpikes = new TreeSet<>();
            for (int idx : spikeIndices) {
                if (idx > 0) paddedSpikes.add(idx - 1);
                paddedSpikes.add(idx);
                if (idx < n - 1) paddedSpikes.add(idx + 1);
            }
            int spikeCount = paddedSpikes.size();
            series.setSpikeCount(spikeCount);

            // 4. Regresja skorygowana (wycięcie szpilek z regresji)
            double adjSumT2 = 0, adjSumTime2 = 0, adjSumTimeT2 = 0, adjSumTimeSq2 = 0;
            int nAdj = 0;
            for (int i = 0; i < n; i++) {
                if (!paddedSpikes.contains(i)) {
                    ThermoMeasurementPoint p = points.get(i);
                    double tHours = ChronoUnit.SECONDS.between(start, p.getTimestampLocal()) / 3600.0;
                    double temp = p.getRawCelsius();
                    adjSumT2 += temp;
                    adjSumTime2 += tHours;
                    adjSumTimeT2 += tHours * temp;
                    adjSumTimeSq2 += tHours * tHours;
                    nAdj++;
                }
            }
            double bAdj = bOrig;
            if (nAdj > 5) {
                double denomAdj = (nAdj * adjSumTimeSq2 - adjSumTime2 * adjSumTime2);
                if (Math.abs(denomAdj) > 1e-9) {
                    bAdj = (nAdj * adjSumTimeT2 - adjSumTime2 * adjSumT2) / denomAdj;
                }
            }
            series.setAdjustedTrendCoefficient(bAdj);

            // 5. Klasyfikacja (Metoda A+ segmentowa)
            double absBOrig24 = Math.abs(bOrig * 24.0);
            double absBAdj24 = Math.abs(bAdj * 24.0);
            double improvement = absBOrig24 > 1e-6 ? (absBOrig24 - absBAdj24) / absBOrig24 : 0;

            if (absBOrig24 <= 0.1 && spikeCount == 0) {
                series.setDriftClassification("STABLE");
            } else if (spikeCount > 0 && absBAdj24 <= 0.1 && (absBOrig24 > 0.1 ? improvement > 0.5 : true)) {
                series.setDriftClassification("SPIKE");
            } else if (spikeCount > 0 && absBAdj24 > 0.1) {
                // Niedoskonałe skorygowanie → analiza segmentów przed i po szpilce
                int spikeMin = ((TreeSet<Integer>) paddedSpikes).first();
                int spikeMax = ((TreeSet<Integer>) paddedSpikes).last();

                List<Double> tempsBefore = new ArrayList<>();
                for (int i = 0; i < spikeMin; i++) {
                    tempsBefore.add(points.get(i).getRawCelsius());
                }
                List<Double> tempsAfter = new ArrayList<>();
                for (int i = spikeMax + 1; i < n; i++) {
                    tempsAfter.add(points.get(i).getRawCelsius());
                }

                double stdBefore = calculateStdDev(tempsBefore);
                double stdAfter = calculateStdDev(tempsAfter);
                double segmentStabilityThreshold = 0.5;

                boolean beforeStable = tempsBefore.size() < 3 || stdBefore <= segmentStabilityThreshold;
                boolean afterStable = tempsAfter.size() < 3 || stdAfter <= segmentStabilityThreshold;

                if (beforeStable && afterStable) {
                    // Oba segmenty stabilne -> szpilka o przesuniętym poziomie (level shift)
                    series.setDriftClassification("SPIKE");
                } else {
                    series.setDriftClassification("MIXED");
                }
            } else {
                series.setDriftClassification("DRIFT");
            }
        } else {
            series.setSpikeCount(0);
            series.setTrendCoefficient(0.0);
            series.setAdjustedTrendCoefficient(0.0);
            series.setDriftClassification("STABLE");
        }

        // --- Budżet niepewności pomiarowej (GUM) ---
        calculateExpandedUncertainty(series);
    }

    /**
     * Wylicza budżet niepewności standardowej i rozszerzonej (k=2) zgodnie z GUM.
     */
    private void calculateExpandedUncertainty(ThermoMeasurementSeries series) {
        List<ThermoMeasurementPoint> points = series.getMeasurements();
        int n = points.size();

        // 1. Niepewność standardowa typu A (statystyczna)
        double uA = series.getStdDeviation() / Math.sqrt(n);

        // 2. Niepewność typu B1 (świadectwo wzorcowania rejestratora)
        double uB1 = 0.05; // Wartość domyślna w przypadku braku certyfikatu
        ThermoRecorder recorder = series.getThermoRecorder();
        if (recorder != null) {
            Calibration cal = recorder.getLatestCalibration();
            if (cal != null && cal.getPoints() != null && !cal.getPoints().isEmpty()) {
                double avgTemp = series.getAvgTemperature();
                CalibrationPoint closestPoint = null;
                double minDiff = Double.MAX_VALUE;
                for (CalibrationPoint p : cal.getPoints()) {
                    double diff = Math.abs(p.getTemperatureValue().doubleValue() - avgTemp);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closestPoint = p;
                    }
                }
                if (closestPoint != null) {
                    // Certyfikat podaje zazwyczaj niepewność rozszerzoną U z k=2 -> standardowa = U/2
                    uB1 = closestPoint.getUncertainty().doubleValue() / 2.0;
                }
            }
        }

        // 3. Niepewność typu B2 (rozdzielczość odczytu rejestratora)
        double resolution = 0.1;
        if (recorder != null && recorder.getResolution() != null) {
            resolution = recorder.getResolution().doubleValue();
        }
        double uB2 = resolution / (2.0 * Math.sqrt(3.0)); // rozkład prostokątny rozdzielczości

        // 4. Złożona standardowa niepewność
        double uC = Math.sqrt(uA * uA + uB1 * uB1 + uB2 * uB2);

        // 5. Rozszerzona niepewność pomiarowa (k = 2 dla ufności ok. 95%)
        double expandedU = 2.0 * uC;

        series.setExpandedUncertainty(expandedU);

        log.info("Zakończono obliczenia GUM dla serii pomiarowej ID: {} | uA: {}, uB1(cal): {}, uB2(res): {} -> U_exp: {}",
                series.getId(), String.format("%.4f", uA), String.format("%.4f", uB1), String.format("%.4f", uB2), String.format("%.4f", expandedU));
    }

    /**
     * Oblicza komplet statystyk na wartościach skorygowanych przez świadectwo wzorcowania.
     * Korekta stosuje interpolację liniową błędu systematycznego (GUM §4.3).
     *
     * <p>Istniejąca metoda {@link #calculateStatistics(ThermoMeasurementSeries)} pozostaje bez zmian.
     * Statystyki surowe i skorygowane są niezależne.</p>
     *
     * @param series      Seria pomiarowa (źródło rawCelsius)
     * @param calibration Świadectwo wzorcowania (null → hasCalibrationData=false)
     * @param lsl         Dolny limit komory [°C] (może być null — wtedy Cp/Cpk nie są liczone)
     * @param usl         Górny limit komory [°C] (może być null)
     * @return {@link CorrectedStatsDTO} z kompletem statystyk skorygowanych
     */
    public CorrectedStatsDTO calculateCorrectedStatistics(
            ThermoMeasurementSeries series,
            Calibration calibration,
            Double lsl,
            Double usl) {

        String posName = series.getGridPosition() != null ? series.getGridPosition().name() : "UNKNOWN";
        String sn = series.getThermoRecorder() != null ? series.getThermoRecorder().getSerialNumber() : "UNKNOWN";

        if (!calibrationCorrectionService.hasCalibrationData(calibration)) {
            log.warn("calculateCorrectedStatistics: brak danych wzorcowania dla serii {} ({})", posName, sn);
            return CorrectedStatsDTO.builder()
                    .positionName(posName)
                    .recorderSerialNumber(sn)
                    .hasCalibrationData(false)
                    .build();
        }

        List<ThermoMeasurementPoint> points = series.getMeasurements();
        if (points == null || points.isEmpty()) {
            return CorrectedStatsDTO.builder()
                    .positionName(posName)
                    .recorderSerialNumber(sn)
                    .hasCalibrationData(false)
                    .build();
        }

        // 1. Pobierz surowe wartości i zastosuj korekcję
        double[] rawValues = points.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).toArray();
        double[] corrected = calibrationCorrectionService.correctValues(rawValues, calibration);
        int n = corrected.length;

        // 2. Podstawowe statystyki opisowe
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : corrected) {
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double avg = sum / n;

        // 3. Odchylenie standardowe próbkowe (n-1, Bessel)
        double varSum = 0.0;
        for (double v : corrected) {
            varSum += (v - avg) * (v - avg);
        }
        double stdDev = n > 1 ? Math.sqrt(varSum / (n - 1)) : 0.0;

        // 4. Mediana
        double[] sortedC = corrected.clone();
        Arrays.sort(sortedC);
        double median;
        if (n % 2 == 0) {
            median = (sortedC[n / 2 - 1] + sortedC[n / 2]) / 2.0;
        } else {
            median = sortedC[n / 2];
        }

        // 5. Cp/Cpk na corrected[] vs LSL/USL (jeśli dostępne)
        Double cpC = null;
        Double cpkC = null;
        if (lsl != null && usl != null) {
            var capability = SpcEngine.calculateCapability(corrected, lsl, usl);
            cpC  = capability.getCp();
            cpkC = capability.getCpk();
        }

        // 6. Rozszerzona niepewność GUM (uA z corrected, uB1+uB2 jak w surowej)
        double uA_star = n > 0 ? stdDev / Math.sqrt(n) : 0.0;

        // uB1: niepewność ze świadectwa wzorcowania (punkt najbliższy średniej)
        double uB1 = 0.05;
        List<CalibrationPoint> calPoints = calibration.getPoints();
        if (calPoints != null && !calPoints.isEmpty()) {
            CalibrationPoint closest = null;
            double minDiff = Double.MAX_VALUE;
            for (CalibrationPoint cp : calPoints) {
                double diff = Math.abs(cp.getTemperatureValue().doubleValue() - avg);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = cp;
                }
            }
            if (closest != null) {
                uB1 = closest.getUncertainty().doubleValue() / 2.0; // U(k=2) → u_std
            }
        }

        // uB2: rozdzielczość rejestratora
        double resolution = 0.1;
        ThermoRecorder recorder = series.getThermoRecorder();
        if (recorder != null && recorder.getResolution() != null) {
            resolution = recorder.getResolution().doubleValue();
        }
        double uB2 = resolution / (2.0 * Math.sqrt(3.0));

        double uC_star = Math.sqrt(uA_star * uA_star + uB1 * uB1 + uB2 * uB2);
        double expandedU_star = 2.0 * uC_star;

        // 7. correctionBias = avg_corrected - avg_raw
        double avgRaw = series.getAvgTemperature() != null ? series.getAvgTemperature() : avg;
        double bias = avg - avgRaw;

        log.debug("calculateCorrectedStatistics [{}]: avg_raw={}, avg*={}, bias={}, U*={}",
                posName,
                String.format("%.4f", avgRaw),
                String.format("%.4f", avg),
                String.format("%.4f", bias),
                String.format("%.4f", expandedU_star));

        return CorrectedStatsDTO.builder()
                .positionName(posName)
                .recorderSerialNumber(sn)
                .hasCalibrationData(true)
                .minCorrected(min)
                .maxCorrected(max)
                .avgCorrected(avg)
                .medianCorrected(median)
                .stdDevCorrected(stdDev)
                .cpCorrected(cpC)
                .cpkCorrected(cpkC)
                .expandedUncertaintyCorrected(expandedU_star)
                .correctionBias(bias)
                .build();
    }

    // --- Metody pomocnicze statystyczne ---

    private double calculateMedian(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    private double calculateStdDev(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;
        double avg = values.stream().mapToDouble(d -> d).average().orElse(0.0);
        // Próbkowa wariancja (n-1) – spójna z SensorStatsEngine i standardem GxP
        double variance = values.stream().mapToDouble(d -> (d - avg) * (d - avg)).sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    private double calculatePercentile(List<Double> sortedValues, double percentile) {
        int n = sortedValues.size();
        if (n == 0) return 0.0;
        if (n == 1) return sortedValues.get(0);
        double rank = (percentile / 100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return sortedValues.get(lower);
        double fraction = rank - lower;
        return sortedValues.get(lower) + fraction * (sortedValues.get(upper) - sortedValues.get(lower));
    }
}
