package com.mac.bry.desktop.service.word;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.helper.MappingValidator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Appendix7DataMapper extends AbstractAppendixDataMapper {

    public Appendix7DataMapper(ValidationPlanNumberRepository validationPlanNumberRepository) {
        super(validationPlanNumberRepository);
    }

    @Override
    public Map<String, String> prepareReplacements(RevalidationSession session) {
        Map<String, String> replacements = new HashMap<>();

        // 1. Dane działu i pracowni
        String deptName = "";
        String labName = "";
        if (session.getCoolingDevice() != null) {
            if (session.getCoolingDevice().getDepartment() != null) {
                deptName = session.getCoolingDevice().getDepartment().getName();
            }
            if (session.getCoolingDevice().getLaboratory() != null) {
                labName = session.getCoolingDevice().getLaboratory().getName();
            }
        }
        replacements.put("$dział$", deptName);
        replacements.put("$pracownia$", labName);

        // 2. Dane urządzenia
        String deviceName = session.getCoolingDevice() != null ? session.getCoolingDevice().getName() : "";
        replacements.put("$nazwaUrzadzenia$", deviceName);

        String invNum = session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "";
        replacements.put("$numerInwentarzowy$", invNum);

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

        replacements.put("$dataPierwszegoOdczytu$", dateStartStr);
        replacements.put("$dataOstatniegoOdczytu$", dateEndStr);

        // 4. Rodzaj składnika
        String materialName = "";
        if (session.getCoolingChamber() != null) {
            materialName = session.getCoolingChamber().getMaterialName();
        }
        replacements.put("$rodzajMaterialu$", materialName);

        // 5. Wyznaczenie punktów krytycznych (Opcja A - Ekstrema absolutne)
        MappingValidator.MappingResult mappingResult = MappingValidator.validate(session);
        RevalidationSession.GridPosition hotspot = mappingResult.isSuccess() ? mappingResult.getHotspot() : null;
        RevalidationSession.GridPosition coldspot = mappingResult.isSuccess() ? mappingResult.getColdspot() : null;

        // 6. Wyniki 8 sensorów
        for (GridPosition pos : GridPosition.values()) {
            int idx = pos.ordinal() + 1;
            String nrKey = "$nrRej" + idx + "$";
            String lokKey = "$loakalizacjaRej" + idx + "$"; // Uwaga: w szablonie jest błąd "loakalizacja"
            String tminKey = "$tminRej" + idx + "$";
            String tmaxKey = "$tmaxRej" + idx + "$";
            String otKey = "$OT" + idx + "$";
            String onKey = "$ON" + idx + "$";

            PositionData posData = session.getAssignedPositions().get(pos);
            if (posData != null) {
                replacements.put(nrKey, posData.getSerialNumber() != null ? posData.getSerialNumber() : "");
                replacements.put(lokKey, pos.getLabel());

                if (posData.getSeries() != null) {
                    Double minTemp = posData.getSeries().getMinTemperature();
                    Double maxTemp = posData.getSeries().getMaxTemperature();
                    replacements.put(tminKey, minTemp != null ? String.format(Locale.US, "%.1f", minTemp) : "");
                    replacements.put(tmaxKey, maxTemp != null ? String.format(Locale.US, "%.1f", maxTemp) : "");
                } else {
                    replacements.put(tminKey, "");
                    replacements.put(tmaxKey, "");
                }

                // Lokalizacja zakwalifikowana do walidacji (TAK jeśli hotspot lub coldspot, NIE w p.p.)
                if (pos == hotspot || pos == coldspot) {
                    replacements.put(otKey, "[X]");
                    replacements.put(onKey, "");
                } else {
                    replacements.put(otKey, "");
                    replacements.put(onKey, "[X]");
                }
            } else {
                replacements.put(nrKey, "");
                replacements.put(lokKey, "");
                replacements.put(tminKey, "");
                replacements.put(tmaxKey, "");
                replacements.put(otKey, "");
                replacements.put(onKey, "");
            }
        }

        populateRpwPlaceholders(replacements, session);

        return replacements;
    }
}
