package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class TestoSimulationService {

    public List<ThermoMeasurementPoint> generateSimulationPoints(int count, int intervalMinutes) {
        List<ThermoMeasurementPoint> pointsList = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.now().minusHours(33);
        Random r = new Random();
        double temp = 4.8;
        
        for (int index = 1; index <= count; index++) {
            double delta = (r.nextDouble() - 0.5) * 0.5;
            temp += delta;
            if (temp < 2.5) temp = 2.8;
            if (temp > 7.0) temp = 6.8;

            double roundedTemp = Math.round(temp * 10.0) / 10.0;
            pointsList.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(index)
                    .timestampLocal(startTime.plusMinutes((long) (index - 1) * intervalMinutes))
                    .rawCelsius(roundedTemp)
                    .build());
        }
        return pointsList;
    }
}
