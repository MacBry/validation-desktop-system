package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class DefrostCycleDetector {

    /**
     * Wykrywa cykle odszraniania w serii pomiarowej pojedynczego czujnika.
     * 
     * @param points Lista punktów pomiarowych.
     * @param rateThreshold Krok gradientu (dT/dt) w °C/min (np. 0.2°C/min).
     * @param amplitudeThreshold Minimalny skok temperatury w °C powyżej bazy (np. 2.0°C).
     * @return Lista wykrytych cykli defrostu.
     */
    public static List<DefrostCycle> detectCycles(
            List<ThermoMeasurementPoint> points, 
            String sensorName,
            double rateThreshold, 
            double amplitudeThreshold) {
        
        List<DefrostCycle> cycles = new ArrayList<>();
        if (points == null || points.size() < 5) {
            return cycles;
        }

        int n = points.size();
        int i = 0;

        while (i < n - 2) {
            ThermoMeasurementPoint startPoint = points.get(i);
            double startTemp = startPoint.getRawCelsius();
            
            // Szukamy początku gwałtownego wzrostu
            int j = i + 1;
            boolean foundStart = false;
            int peakIdx = -1;
            double maxTemp = startTemp;

            // Sprawdzamy okno czasowe do 45 minut w przód w celu znalezienia piku
            while (j < n) {
                ThermoMeasurementPoint currentPoint = points.get(j);
                long durationMinutes = ChronoUnit.MINUTES.between(startPoint.getTimestampLocal(), currentPoint.getTimestampLocal());
                
                if (durationMinutes > 45) {
                    break; // cykl defrostu nie powinien narastać dłużej niż 45 minut
                }

                double currentTemp = currentPoint.getRawCelsius();
                if (currentTemp > maxTemp) {
                    maxTemp = currentTemp;
                    peakIdx = j;
                }

                // Sprawdzamy gradient w tym oknie
                ThermoMeasurementPoint prevPoint = points.get(j - 1);
                double dt = ChronoUnit.SECONDS.between(prevPoint.getTimestampLocal(), currentPoint.getTimestampLocal()) / 60.0;
                if (dt > 0) {
                    double rate = (currentTemp - prevPoint.getRawCelsius()) / dt;
                    if (rate >= rateThreshold && (maxTemp - startTemp) >= amplitudeThreshold) {
                        foundStart = true;
                    }
                }
                j++;
            }

            if (foundStart && peakIdx != -1) {
                // Znaleźliśmy defrost! Teraz szukamy końca cyklu (gdzie temperatura wraca do normy)
                int endIdx = peakIdx;
                double baseline = startTemp;

                // Szukamy punktu powrotu do temperatury bazowej (z tolerancją +0.5°C) lub stabilizacji
                int k = peakIdx + 1;
                while (k < n) {
                    ThermoMeasurementPoint checkPoint = points.get(k);
                    long durationFromStart = ChronoUnit.MINUTES.between(startPoint.getTimestampLocal(), checkPoint.getTimestampLocal());
                    
                    if (durationFromStart > 180) {
                        endIdx = k; // ograniczamy maksymalną długość cyklu do 3 godzin
                        break;
                    }

                    double checkTemp = checkPoint.getRawCelsius();
                    if (checkTemp <= baseline + 0.5) {
                        endIdx = k;
                        break;
                    }
                    
                    // Sprawdź czy nie zaczyna się kolejny wzrost (lokalne minimum)
                    if (k < n - 1 && checkTemp < points.get(k - 1).getRawCelsius() && checkTemp < points.get(k + 1).getRawCelsius()) {
                        endIdx = k;
                    }
                    
                    endIdx = k;
                    k++;
                }

                ThermoMeasurementPoint endPoint = points.get(endIdx);
                double duration = ChronoUnit.SECONDS.between(startPoint.getTimestampLocal(), endPoint.getTimestampLocal()) / 60.0;
                double amplitude = maxTemp - baseline;

                cycles.add(new DefrostCycle(
                        sensorName,
                        startPoint.getTimestampLocal(),
                        endPoint.getTimestampLocal(),
                        duration,
                        maxTemp,
                        amplitude
                ));

                // Przesuwamy indeks główny pętli za wykryty cykl
                i = endIdx + 1;
            } else {
                i++;
            }
        }

        return cycles;
    }
}
