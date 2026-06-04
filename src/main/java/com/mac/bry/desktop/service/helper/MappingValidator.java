package com.mac.bry.desktop.service.helper;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.service.hotspot.SensorStats;
import lombok.Value;

import java.util.*;

/**
 * Walidator biznesowy dla procedury mapowania rozkładu temperatur (PDA TR-64)
 * wykorzystujący silnik konsensusu metodycznego.
 */
public class MappingValidator {

    @Value
    public static class MappingResult {
        boolean success;
        String errorMessage;
        RevalidationSession.GridPosition hotspot;
        RevalidationSession.GridPosition coldspot;
        Double maxTemperature;
        Double minTemperature;
        boolean weakConsensus;
        double hotspotStrength;
        double coldspotStrength;
    }

    /**
     * Dokonuje analizy 8 serii pomiarowych w sesji i wyznacza punkty krytyczne metodą konsensusu.
     */
    public static MappingResult validate(RevalidationSession session) {
        // 1. Sprawdzenie czy wgrano komplet 8 czujników
        Map<RevalidationSession.GridPosition, RevalidationSession.PositionData> assigned = session.getAssignedPositions();
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            if (!assigned.containsKey(pos) || assigned.get(pos).getSeries() == null) {
                return new MappingResult(false, "Brak wgranej serii pomiarowej dla pozycji: " + pos.getLabel(), null, null, null, null, false, 0.0, 0.0);
            }
        }

        // 2. Wyznaczenie globalnych ekstremów (dla kompatybilności raportowania)
        Double globalMax = null;
        Double globalMin = null;

        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            ThermoMeasurementSeries series = assigned.get(pos).getSeries();
            for (ThermoMeasurementPoint point : series.getMeasurements()) {
                double val = point.getRawCelsius();
                if (globalMax == null || val > globalMax) {
                    globalMax = val;
                }
                if (globalMin == null || val < globalMin) {
                    globalMin = val;
                }
            }
        }

        if (globalMax == null || globalMin == null) {
            return new MappingResult(false, "Brak danych pomiarowych w seriach.", null, null, null, null, false, 0.0, 0.0);
        }

        // 3. Budowanie listy SensorStats dla serwisu detekcji
        List<SensorStats> sensorStatsList = new ArrayList<>();
        Double minLimit = session.getCoolingChamber() != null ? session.getCoolingChamber().getMinOperatingTemp() : null;
        Double maxLimit = session.getCoolingChamber() != null ? session.getCoolingChamber().getMaxOperatingTemp() : null;

        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            ThermoMeasurementSeries series = assigned.get(pos).getSeries();
            List<ThermoMeasurementPoint> measurements = series.getMeasurements();

            // a. Podstawowe parametry wyliczone przy imporcie lub dynamicznie
            double absMax = series.getMaxTemperature() != null ? series.getMaxTemperature() :
                    measurements.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).max().orElse(globalMax);
            double absMin = series.getMinTemperature() != null ? series.getMinTemperature() :
                    measurements.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).min().orElse(globalMin);
            double mean = series.getAvgTemperature() != null ? series.getAvgTemperature() :
                    measurements.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).average().orElse(absMax);
            double mkt = series.getMktTemperature() != null ? series.getMktTemperature() :
                    calculateMkt(measurements, mean);

            // b. Obliczenie percentyli P99 i P01
            List<Double> sortedTemps = measurements.stream()
                    .map(ThermoMeasurementPoint::getRawCelsius)
                    .sorted()
                    .toList();
            double p99 = calculatePercentile(sortedTemps, 99.0);
            double p01 = calculatePercentile(sortedTemps, 1.0);

            // c. Obliczenie całki przekroczeń limitów (w stopnio-minutach)
            double tolHi = 0.0;
            double tolLo = 0.0;
            int interval = series.getLoggingIntervalMinutes() != null ? series.getLoggingIntervalMinutes() : 5;

            for (ThermoMeasurementPoint point : measurements) {
                double temp = point.getRawCelsius();
                if (maxLimit != null && temp > maxLimit) {
                    tolHi += (temp - maxLimit) * interval;
                }
                if (minLimit != null && temp < minLimit) {
                    tolLo += (minLimit - temp) * interval;
                }
            }

            sensorStatsList.add(new SensorStats(
                    pos.name(),
                    absMax,
                    absMin,
                    mean,
                    p99,
                    p01,
                    mkt,
                    tolHi,
                    tolLo
            ));
        }

        // 4. Inicjalizacja strategii i silnika konsensusu
        List<com.mac.bry.desktop.service.hotspot.ExtremeDetectionStrategy> strategies = List.of(
                new com.mac.bry.desktop.service.hotspot.AbsMaxStrategy(),
                new com.mac.bry.desktop.service.hotspot.AbsMinStrategy(),
                new com.mac.bry.desktop.service.hotspot.MeanStrategy(true),
                new com.mac.bry.desktop.service.hotspot.MeanStrategy(false),
                new com.mac.bry.desktop.service.hotspot.MktStrategy(),
                new com.mac.bry.desktop.service.hotspot.PercentileStrategy(true),
                new com.mac.bry.desktop.service.hotspot.PercentileStrategy(false),
                new com.mac.bry.desktop.service.hotspot.TimeOverLimitStrategy(true),
                new com.mac.bry.desktop.service.hotspot.TimeOverLimitStrategy(false)
        );

        com.mac.bry.desktop.service.hotspot.ConsensusDetectionService consensusService =
                new com.mac.bry.desktop.service.hotspot.ConsensusDetectionService(strategies);

        var hotspotReport = consensusService.detectHotspot(sensorStatsList);
        var coldspotReport = consensusService.detectColdspot(sensorStatsList);

        if (!hotspotReport.hasDetection() || !coldspotReport.hasDetection()) {
            return new MappingResult(false, "Nie udało się jednoznacznie wyznaczyć punktów krytycznych.", null, null, globalMax, globalMin, false, 0.0, 0.0);
        }

        RevalidationSession.GridPosition hotspot = RevalidationSession.GridPosition.valueOf(hotspotReport.consensusSensorId());
        RevalidationSession.GridPosition coldspot = RevalidationSession.GridPosition.valueOf(coldspotReport.consensusSensorId());

        if (hotspot == coldspot) {
            return new MappingResult(false, "Kolizja punktów: Hotspot i Coldspot wyznaczone na tej samej pozycji: " + hotspot.getLabel(), null, null, globalMax, globalMin, false, 0.0, 0.0);
        }

        boolean weakConsensus = hotspotReport.isWeak() || coldspotReport.isWeak();

        return new MappingResult(
                true,
                null,
                hotspot,
                coldspot,
                globalMax,
                globalMin,
                weakConsensus,
                hotspotReport.consensusStrength(),
                coldspotReport.consensusStrength()
        );
    }

    private static double calculatePercentile(List<Double> sortedValues, double percentile) {
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

    private static double calculateMkt(List<ThermoMeasurementPoint> pts, double fallback) {
        int n = pts.size();
        if (n == 0) return fallback;
        double gasConstant = 8.314472;
        double deltaH = 83.14 * 1000.0;
        double mktSum = 0.0;
        for (ThermoMeasurementPoint pt : pts) {
            double tempKelvin = pt.getRawCelsius() + 273.15;
            mktSum += Math.exp(-deltaH / (gasConstant * tempKelvin));
        }
        if (mktSum > 0) {
            return (deltaH / gasConstant) / (-Math.log(mktSum / n)) - 273.15;
        }
        return fallback;
    }
}

