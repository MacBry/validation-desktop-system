package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GxPProcedureService {

    private final ThermoMeasurementSeriesRepository seriesRepository;

    public List<ProcedureRow> loadProcedures() {
        List<ThermoMeasurementSeries> allSeries = seriesRepository.findAll();
        
        // Grupowanie serii po revalidationGroupId (lub komorze i czasie startu jako fallback)
        Map<String, List<ThermoMeasurementSeries>> groups = new HashMap<>();
        for (ThermoMeasurementSeries series : allSeries) {
            if (series.getCoolingChamber() == null) {
                continue;
            }
            String key = series.getRevalidationGroupId() != null 
                    ? series.getRevalidationGroupId() 
                    : (series.getFirstMeasurementTimeLocal() != null 
                        ? series.getCoolingChamber().getId() + "_" + series.getFirstMeasurementTimeLocal().toString()
                        : "unknown_" + series.getCoolingChamber().getId());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(series);
        }

        List<ProcedureRow> rows = new ArrayList<>();
        
        for (var entry : groups.entrySet()) {
            List<ThermoMeasurementSeries> seriesList = entry.getValue();
            seriesList.sort((s1, s2) -> {
                if (s1.getGridPosition() != null && s2.getGridPosition() != null) {
                    return Integer.compare(s1.getGridPosition().ordinal(), s2.getGridPosition().ordinal());
                }
                return s1.getThermoRecorder().getSerialNumber().compareTo(s2.getThermoRecorder().getSerialNumber());
            });
            
            ThermoMeasurementSeries representative = seriesList.get(0);
            CoolingChamber chamber = representative.getCoolingChamber();
            CoolingDevice device = chamber.getCoolingDevice();

            String typeName;
            if (representative.getProcedureType() == GxPProcedureType.MAPPING) {
                typeName = "Mapowanie Komory (PDA TR-64)";
            } else {
                typeName = seriesList.size() >= 8 
                        ? "Rewalidacja Komory (Kwalifikacja 8-Kanałowa)" 
                        : (seriesList.size() > 1 
                            ? "Rewalidacja Komory (Kwalifikacja " + seriesList.size() + "-Kanałowa)" 
                            : "Pojedynczy Odczyt Kontrolny");
            }

            String locationName = (device != null ? device.getName() + " [" + device.getInventoryNumber() + "]" : "Brak urządzenia")
                    + " / " + chamber.getChamberName();

            LocalDateTime maxImportedAt = seriesList.stream()
                    .map(ThermoMeasurementSeries::getImportedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(representative.getImportedAt());

            String dateStr = maxImportedAt != null 
                    ? maxImportedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) 
                    : "";

            String sensorSnList = seriesList.stream()
                    .map(s -> s.getThermoRecorder().getSerialNumber())
                    .collect(java.util.stream.Collectors.joining(", "));

            int totalMeasurements = seriesList.stream()
                    .mapToInt(ThermoMeasurementSeries::getMeasurementsCount)
                    .sum();

            boolean hasInvalidCal = false;
            for (ThermoMeasurementSeries s : seriesList) {
                Calibration cal = s.getThermoRecorder().getLatestCalibration();
                if (cal == null || !cal.isValid()) {
                    hasInvalidCal = true;
                    break;
                }
            }
            String gxpStatus = hasInvalidCal ? "OSTRZEŻENIE GxP" : "ZATWIERDZONA GxP";

            rows.add(ProcedureRow.builder()
                    .type(typeName)
                    .location(locationName)
                    .dateImported(dateStr)
                    .sensors(sensorSnList)
                    .measurementsCount(totalMeasurements)
                    .gxpStatus(gxpStatus)
                    .associatedSeries(seriesList)
                    .device(device)
                    .chamber(chamber)
                    .build());
        }

        // Sortowanie od najnowszych
        rows.sort((r1, r2) -> r2.getDateImported().compareTo(r1.getDateImported()));
        return rows;
    }

    public List<DetailRow> loadDetailRows(List<ThermoMeasurementSeries> seriesList) {
        List<DetailRow> details = new ArrayList<>();
        var positions = RevalidationSession.GridPosition.values();
        
        for (int i = 0; i < seriesList.size(); i++) {
            ThermoMeasurementSeries s = seriesList.get(i);
            RevalidationSession.GridPosition pos = s.getGridPosition() != null 
                    ? s.getGridPosition() 
                    : positions[i % positions.length];
            
            double min = s.getMinTemperature() != null ? s.getMinTemperature() : 0.0;
            double max = s.getMaxTemperature() != null ? s.getMaxTemperature() : 0.0;
            double avg = s.getAvgTemperature() != null ? s.getAvgTemperature() : 0.0;
            double mkt = s.getMktTemperature() != null ? s.getMktTemperature() : 0.0;
            double unc = s.getExpandedUncertainty() != null ? s.getExpandedUncertainty() : 0.0;
            int spikes = s.getSpikeCount() != null ? s.getSpikeCount() : 0;
            String drift = s.getDriftClassification() != null ? s.getDriftClassification() : "STABLE";

            details.add(DetailRow.builder()
                    .positionName(pos.getLabel())
                    .serialNumber(s.getThermoRecorder().getSerialNumber())
                    .minTemp(String.format("%.1f°C", min))
                    .maxTemp(String.format("%.1f°C", max))
                    .avgTemp(String.format("%.1f°C", avg))
                    .mktTemp(String.format("%.1f°C", mkt))
                    .uncertainty(String.format("±%.3f°C", unc))
                    .spikes(String.valueOf(spikes))
                    .driftClassification(drift)
                    .build());
        }

        // Sortowanie wierszy szczegółów według fizycznej pozycji na siatce 3D (kolejność w enumie GridPosition)
        details.sort(Comparator.comparingInt(d -> {
            for (var p : positions) {
                if (p.getLabel().equals(d.getPositionName())) {
                    return p.ordinal();
                }
            }
            return 999;
        }));

        return details;
    }
}
