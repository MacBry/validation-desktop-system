package com.mac.bry.desktop.service.word;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Appendix3DataMapper extends AbstractAppendixDataMapper {

    public Appendix3DataMapper(ValidationPlanNumberRepository validationPlanNumberRepository) {
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
        replacements.put("$dzial$", deptName);

        String labName = "";
        if (session.getCoolingDevice() != null && session.getCoolingDevice().getLaboratory() != null) {
            labName = session.getCoolingDevice().getLaboratory().getName();
        }
        replacements.put("$pracownia$", labName);

        // 2. Cel walidacji
        String celVal = "";
        if (session.getProcedureType() != null) {
            celVal = session.getProcedureType().getDisplayName();
        } else {
            celVal = "Rewalidacja okresowa";
        }
        replacements.put("$CelWalidacji$", celVal);

        // 3. Rodzaj materiału
        String materialName = "";
        if (session.getCoolingChamber() != null) {
            materialName = session.getCoolingChamber().getMaterialName();
        }
        replacements.put("$typMaterialu$", materialName);

        // 4. Obliczanie zakresu dat na podstawie serii pomiarowych
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

        replacements.put("$data1odczytu$", dateStartStr);
        replacements.put("$dataOstatniegoOdczytu$", dateEndStr);

        // 5. Dane urządzenia i komory
        String deviceName = session.getCoolingDevice() != null ? session.getCoolingDevice().getName() : "";
        replacements.put("$nazwaUrzadzenia$", deviceName);

        String chamberTypeStr = "";
        if (session.getCoolingChamber() != null && session.getCoolingChamber().getChamberType() != null) {
            chamberTypeStr = session.getCoolingChamber().getChamberType().getDisplayName();
        }
        replacements.put("$typKomory$", chamberTypeStr);

        String invNum = session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "";
        replacements.put("$numerSeryjnyUrzadzenia$", invNum);

        // 6. Kryteria akceptacji (Sekcja 7)
        String o1 = "", o2 = "", o3 = "", o4 = "", o5 = "", o6 = "", o7 = "";
        String inneOpis = "", inneMin = "", inneMax = "";

        if (session.getCoolingChamber() != null) {
            CoolingChamber chamber = session.getCoolingChamber();
            ChamberType type = chamber.getChamberType();
            String mat = chamber.getMaterialName() != null ? chamber.getMaterialName() : "";
            Double min = chamber.getMinOperatingTemp();
            Double max = chamber.getMaxOperatingTemp();

            if ((type == ChamberType.FRIDGE || type == ChamberType.COLD_ROOM) 
                    && (mat.toUpperCase().contains("KKCZ") || (min != null && min == 2.0 && max != null && max == 6.0))) {
                o4 = "[X]";
            }
            else if (mat.toUpperCase().contains("KKP") || (min != null && min == 20.0 && max != null && max == 24.0)) {
                o5 = "[X]";
            }
            else if ((type == ChamberType.FRIDGE || type == ChamberType.COLD_ROOM)
                    && (mat.toUpperCase().contains("ODCZYNNIK") || mat.toUpperCase().contains("PRÓB") || mat.toUpperCase().contains("PROB") || (min != null && min == 2.0 && max != null && max == 8.0))) {
                o6 = "[X]";
            }
            else if (type == ChamberType.FREEZER 
                    && (mat.toUpperCase().contains("FFP") || (max != null && max == -18.0))) {
                o2 = "[X]";
            }
            else if (type == ChamberType.FREEZER 
                    && (mat.toUpperCase().contains("ODCZYNNIK") || mat.toUpperCase().contains("PRÓB") || mat.toUpperCase().contains("PROB") || (max != null && max == -20.0))) {
                o3 = "[X]";
            }
            else if ((type == ChamberType.FREEZER || type == ChamberType.FREEZE_ROOM) 
                    && (max != null && max <= -25.0)) {
                o1 = "[X]";
            }
            else {
                o7 = "[X]";
                inneOpis = (type != null ? type.getDisplayName() : "") + " do przechowywania: " + mat;
                inneMin = min != null ? String.format(Locale.US, "%.1f", min) : "—";
                inneMax = max != null ? String.format(Locale.US, "%.1f", max) : "—";
            }
        }

        replacements.put("$o1$", o1);
        replacements.put("$o2$", o2);
        replacements.put("$o3$", o3);
        replacements.put("$o4$", o4);
        replacements.put("$o5$", o5);
        replacements.put("$o6$", o6);
        replacements.put("$o7$", o7);
        replacements.put("$InneOpis$", inneOpis);
        replacements.put("$InneMin$", inneMin);
        replacements.put("$InneMax$", inneMax);

        // 7. Wyniki walidacji (Tabela 1-8 rejestratorów)
        double sumAvg = 0;
        int activeCount = 0;
        boolean revalidationSuccess = true;
        List<String> violations = new ArrayList<>();
        Double minTempLimit = session.getCoolingChamber() != null ? session.getCoolingChamber().getMinOperatingTemp() : null;
        Double maxTempLimit = session.getCoolingChamber() != null ? session.getCoolingChamber().getMaxOperatingTemp() : null;

        for (GridPosition pos : GridPosition.values()) {
            int idx = pos.ordinal() + 1;
            String snKey = "$nrSerRej" + idx + "$";
            String dateWzorKey = "$dataWzorcowaniaRej" + idx + "$";
            String certKey = "$NrCertRej" + idx + "$";
            String lokKey = "$lokalizacjaRej" + idx + "$";
            String tminKey = "$TminRej" + idx + "$";
            String tmaxKey = "$TmaxRej" + idx + "$";
            String tavgKey = "$TavgRej" + idx + "$";

            PositionData posData = session.getAssignedPositions().get(pos);
            if (posData != null) {
                replacements.put(snKey, posData.getSerialNumber() != null ? posData.getSerialNumber() : "");
                
                Calibration cal = posData.getLatestCalibration();
                if (cal != null && cal.getCalibrationDate() != null) {
                    replacements.put(dateWzorKey, cal.getCalibrationDate().format(dateFormatter));
                } else {
                    replacements.put(dateWzorKey, "");
                }
                
                if (cal != null && cal.getCertificateNumber() != null) {
                    replacements.put(certKey, cal.getCertificateNumber());
                } else {
                    replacements.put(certKey, "");
                }
                
                replacements.put(lokKey, pos.getLabel());
                
                if (posData.getSeries() != null) {
                    Double minTemp = posData.getSeries().getMinTemperature();
                    Double maxTemp = posData.getSeries().getMaxTemperature();
                    Double avgTemp = posData.getSeries().getAvgTemperature();

                    replacements.put(tminKey, minTemp != null ? String.format(Locale.US, "%.1f", minTemp) : "");
                    replacements.put(tmaxKey, maxTemp != null ? String.format(Locale.US, "%.1f", maxTemp) : "");
                    replacements.put(tavgKey, avgTemp != null ? String.format(Locale.US, "%.1f", avgTemp) : "");

                    if (avgTemp != null) {
                        sumAvg += avgTemp;
                        activeCount++;
                    }

                    // Weryfikacja przekroczeń
                    if (posData.getSeries().getMeasurements() != null) {
                        for (ThermoMeasurementPoint pt : posData.getSeries().getMeasurements()) {
                            Double tempVal = pt.getRawCelsius();
                            if (tempVal != null) {
                                if (minTempLimit != null && tempVal < minTempLimit) {
                                    revalidationSuccess = false;
                                    String violationMsg = pos.getLabel() + " (" + String.format(Locale.US, "%.1f", tempVal) + "°C < " + String.format(Locale.US, "%.1f", minTempLimit) + "°C)";
                                    if (!violations.contains(violationMsg)) {
                                        violations.add(violationMsg);
                                    }
                                }
                                if (maxTempLimit != null && tempVal > maxTempLimit) {
                                    revalidationSuccess = false;
                                    String violationMsg = pos.getLabel() + " (" + String.format(Locale.US, "%.1f", tempVal) + "°C > " + String.format(Locale.US, "%.1f", maxTempLimit) + "°C)";
                                    if (!violations.contains(violationMsg)) {
                                        violations.add(violationMsg);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    replacements.put(tminKey, "");
                    replacements.put(tmaxKey, "");
                    replacements.put(tavgKey, "");
                }
            } else {
                replacements.put(snKey, "");
                replacements.put(dateWzorKey, "");
                replacements.put(certKey, "");
                replacements.put(lokKey, "");
                replacements.put(tminKey, "");
                replacements.put(tmaxKey, "");
                replacements.put(tavgKey, "");
            }
        }

        // 8. Średnia temperatura w urządzeniu
        String avgTotalStr = activeCount > 0 ? String.format(Locale.US, "%.1f°C", sumAvg / activeCount) : "";
        replacements.put("$AVGTemUrzadzenia$", avgTotalStr);

        // 9. Wnioskowanie i Akceptacja
        String tak = "", nie = "";
        String wnioski = "", uwagi = "";
        String nextValDate = "";

        String formattedMin = minTempLimit != null ? String.format(Locale.US, "%.1f", minTempLimit) : "—";
        String formattedMax = maxTempLimit != null ? String.format(Locale.US, "%.1f", maxTempLimit) : "—";
        String matName = session.getCoolingChamber() != null ? session.getCoolingChamber().getMaterialName() : "";
        String avgTempFormatted = activeCount > 0 ? String.format(Locale.US, "%.1f", sumAvg / activeCount) : "";

        if (revalidationSuccess) {
            tak = "[X]";
            nie = "";
            wnioski = "Na podstawie analizy przestrzennej rozkładu temperatur w komorze stwierdza się, "
                    + "że urządzenie spełnia kryteria akceptacji. Urządzenie pracuje stabilnie w zadanym zakresie roboczym ("
                    + formattedMin + "°C do " + formattedMax + "°C). Średnia temperatura komory wynosi " + avgTempFormatted + "°C.";
            uwagi = "Brak uwag. Walidacja zakończona wynikiem pozytywnym. Warunki przechowywania materiału ("
                    + matName + ") są zgodne z wymaganiami.";
            
            if (lastEnd != null) {
                nextValDate = lastEnd.plusYears(1).format(dateFormatter);
            } else {
                nextValDate = LocalDateTime.now().plusYears(1).format(dateFormatter);
            }
        } else {
            tak = "";
            nie = "[X]";
            wnioski = "UWAGA! Na podstawie analizy przestrzennej rozkładu temperatur stwierdza się, "
                    + "że urządzenie NIE SPEŁNIA kryteriów akceptacji. Wykryto przekroczenia dopuszczalnego zakresu roboczego komory ("
                    + formattedMin + "°C do " + formattedMax + "°C).";
            
            StringBuilder sbUwagi = new StringBuilder("Wykryto przekroczenia dopuszczalnej temperatury w następujących pozycjach: ");
            for (int i = 0; i < violations.size(); i++) {
                sbUwagi.append(violations.get(i));
                if (i < violations.size() - 1) {
                    sbUwagi.append(", ");
                }
            }
            sbUwagi.append(". Wymagany przegląd serwisowy urządzenia oraz powtórzenie procedury rewalidacji.");
            uwagi = sbUwagi.toString();
            nextValDate = "NIEZWŁOCZNIE PO PODJĘTYCH DZIAŁANIACH NAPRAWCZYCH";
        }

        replacements.put("$tak$", tak);
        replacements.put("$nie$", nie);
        replacements.put("$Wnioski$", wnioski);
        replacements.put("$Uwagi$", uwagi);
        replacements.put("$dataNastepnejWalidacji$", nextValDate);

        populateRpwPlaceholders(replacements, session);

        return replacements;
    }
}
