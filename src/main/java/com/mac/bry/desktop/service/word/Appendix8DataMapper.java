package com.mac.bry.desktop.service.word;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Appendix8DataMapper extends AbstractAppendixDataMapper {

    public Appendix8DataMapper(ValidationPlanNumberRepository validationPlanNumberRepository) {
        super(validationPlanNumberRepository);
    }

    @Override
    public Map<String, String> prepareReplacements(RevalidationSession session) {
        Map<String, String> replacements = new HashMap<>();

        // 1. Dane działu i pracowni
        String deptName = "";
        if (session.getCoolingDevice() != null && session.getCoolingDevice().getDepartment() != null) {
            deptName = session.getCoolingDevice().getDepartment().getName();
        }
        replacements.put("$dział$", deptName);
        replacements.put("$dzia\u0142$", deptName); // Zabezpieczenie kodowania 'ł'

        String labName = "";
        if (session.getCoolingDevice() != null && session.getCoolingDevice().getLaboratory() != null) {
            labName = session.getCoolingDevice().getLaboratory().getName();
        }
        replacements.put("%pracownia$", labName);

        // 2. Dane urządzenia i komory
        String deviceName = session.getCoolingDevice() != null ? session.getCoolingDevice().getName() : "";
        replacements.put("$nazwaUrzadzenia$", deviceName);

        String invNum = session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "";
        replacements.put("$numerInw$", invNum);

        String material = "";
        if (session.getCoolingChamber() != null && session.getCoolingChamber().getMaterialType() != null
                && session.getCoolingChamber().getMaterialType().getName() != null) {
            material = session.getCoolingChamber().getMaterialType().getName();
        }
        replacements.put("$material$", material);

        // 3. Obliczanie zakresu dat na podstawie serii pomiarowych
        String dateStartStr = "";
        String dateEndStr = "";
        LocalDateTime firstStart = null;
        LocalDateTime lastEnd = null;

        for (PositionData posData : session.getAssignedPositions().values()) {
            if (posData.getSeries() != null) {
                LocalDateTime start = posData.getSeries().getFirstMeasurementTimeLocal();
                if (start != null && (firstStart == null || start.isBefore(firstStart))) {
                    firstStart = start;
                }
                if (start != null && posData.getSeries().getLoggingIntervalMinutes() != null 
                        && posData.getSeries().getMeasurementsCount() != null) {
                    LocalDateTime end = start.plusMinutes(
                        (long) posData.getSeries().getLoggingIntervalMinutes() * (posData.getSeries().getMeasurementsCount() - 1)
                    );
                    if (lastEnd == null || end.isAfter(lastEnd)) {
                        lastEnd = end;
                    }
                }
            }
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (firstStart != null) {
            dateStartStr = firstStart.format(dateFormatter);
        }
        if (lastEnd != null) {
            dateEndStr = lastEnd.format(dateFormatter);
        }

        replacements.put("$dataStart$", dateStartStr);
        replacements.put("$dataKoniec$", dateEndStr);

        // 4. Mapowanie 8 pozycji siatki (rejestratory, temperatury i X-y)
        for (GridPosition pos : GridPosition.values()) {
            int idx = pos.ordinal() + 1;
            String posKey = "$" + idx + "$";
            String snKey = "$nrSerREJ" + idx + "$";
            String tmaxKey = "$tmax" + idx + "$";
            String tminKey = "$tmin" + idx + "$";

            PositionData posData = session.getAssignedPositions().get(pos);
            if (posData != null) {
                replacements.put(posKey, "X");
                replacements.put(snKey, posData.getSerialNumber() != null ? posData.getSerialNumber() : "");

                if (posData.getSeries() != null) {
                    Double maxTemp = posData.getSeries().getMaxTemperature();
                    Double minTemp = posData.getSeries().getMinTemperature();
                    replacements.put(tmaxKey, maxTemp != null ? String.format(Locale.US, "%.1f", maxTemp) : "");
                    replacements.put(tminKey, minTemp != null ? String.format(Locale.US, "%.1f", minTemp) : "");
                } else {
                    replacements.put(tmaxKey, "");
                    replacements.put(tminKey, "");
                }
            } else {
                // Pozycja nieużywana - zamieniamy na puste wartości
                replacements.put(posKey, "");
                replacements.put(snKey, "");
                replacements.put(tmaxKey, "");
                replacements.put(tminKey, "");
            }
        }

        populateRpwPlaceholders(replacements, session);

        return replacements;
    }
}
