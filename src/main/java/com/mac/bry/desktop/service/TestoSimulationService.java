package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Serwis generujący symulowane punkty pomiarowe dla celów walidacji i demonstracji.
 * Obsługuje profile stabilne, z dryftem, ze spikami defrostu oraz ich kombinacje.
 */
@Service
public class TestoSimulationService {

    public List<ThermoMeasurementPoint> generateSimulationPoints(int count, int intervalMinutes) {
        return generateSimulationPoints(count, intervalMinutes, SimulationProfile.STABLE, 4.8, 1, LocalDateTime.now().minusHours(33));
    }

    public List<ThermoMeasurementPoint> generateSimulationPoints(
            int count,
            int intervalMinutes,
            SimulationProfile profile,
            double baselineTemp,
            int seedIndex,
            LocalDateTime startTimeLocal) {
        
        List<ThermoMeasurementPoint> pointsList = new ArrayList<>();
        Random r = new Random(seedIndex * 1337L);
        double temp = baselineTemp;
        
        for (int index = 1; index <= count; index++) {
            // Fluktuacje
            double delta = (r.nextDouble() - 0.5) * 0.3;
            
            // 1. Dodanie dryftu temperaturowego (powolny wzrost)
            if (profile == SimulationProfile.DRIFT || profile == SimulationProfile.DRIFT_AND_SPIKES) {
                // Stały dryft w górę o 0.03 stopnia na pomiar
                delta += 0.03;
            }
            
            temp += delta;
            
            // Zabezpieczenie przed ucieczką baseline
            if (profile == SimulationProfile.STABLE) {
                if (temp < baselineTemp - 0.6) temp = baselineTemp - 0.4;
                if (temp > baselineTemp + 0.6) temp = baselineTemp + 0.4;
            } else if (profile == SimulationProfile.DRIFT || profile == SimulationProfile.DRIFT_AND_SPIKES) {
                if (temp < baselineTemp - 1.0) temp = baselineTemp - 0.8;
                if (temp > baselineTemp + 5.0) temp = baselineTemp + 4.8;
            }

            double finalTemp = temp;

            // 2. Dodanie spików (defrosty) co 15 pomiarów (symulacja nagłych skoków o 3.5°C na 1-2 pomiary)
            if (profile == SimulationProfile.SPIKES || profile == SimulationProfile.DRIFT_AND_SPIKES) {
                if (index % 12 == 0) {
                    finalTemp += 3.5;
                } else if (index % 12 == 1) {
                    finalTemp += 1.8;
                }
            }

            double roundedTemp = Math.round(finalTemp * 10.0) / 10.0;
            pointsList.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(index)
                    .timestampLocal(startTimeLocal.plusMinutes((long) (index - 1) * intervalMinutes))
                    .rawCelsius(roundedTemp)
                    .build());
        }
        return pointsList;
    }
}
