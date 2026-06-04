package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.helper.MappingValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serwis odpowiedzialny za ładowanie szablonu Word DOCX dla Załącznika nr 8, Załącznika nr 3 i Załącznika nr 7,
 * dynamiczną podmianę znaczników GxP i zapis gotowych raportów.
 */
@Service
@Slf4j
public class TestoRevalidationWordService {

    private static final String TEMPLATE_PATH = "/templates/appendix_8_template.docx";
    private static final String TEMPLATE_3_PATH = "/templates/appendix_3_template.docx";
    private static final String TEMPLATE_7_PATH = "/templates/appendix_7_template.docx";

    @Autowired(required = false)
    private ValidationPlanNumberRepository validationPlanNumberRepository;

    public void setValidationPlanNumberRepository(ValidationPlanNumberRepository validationPlanNumberRepository) {
        this.validationPlanNumberRepository = validationPlanNumberRepository;
    }

    /**
     * Uzupełnia szablon Załącznika nr 8 danymi z sesji rewalidacji i zapisuje go do wskazanego strumienia wyjściowego.
     *
     * @param session      Sesja rewalidacji zawierająca dane urządzenia i serii pomiarowych
     * @param outputStream Strumień, do którego zostanie zapisany wyjściowy plik DOCX
     * @throws Exception W przypadku błędu wejścia/wyjścia lub parsowania dokumentu Word
     */
    public void generateAppendix8(RevalidationSession session, OutputStream outputStream) throws Exception {
        log.info("Rozpoczęcie generowania Załącznika nr 8 (Word DOCX) dla urządzenia: {}", 
                session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "NULL");

        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Nie odnaleziono szablonu Załącznika nr 8 pod ścieżką w zasobach: " + TEMPLATE_PATH);
            }

            try (XWPFDocument doc = new XWPFDocument(is)) {
                Map<String, String> replacements = prepareReplacements(session);

                replacePlaceholders(doc, replacements);

                doc.write(outputStream);
                log.info("Załącznik nr 8 został pomyślnie wygenerowany i zapisany.");
            }
        }
    }

    /**
     * Przygotowuje mapę podmian dla wszystkich znaczników w dokumencie.
     */
    private Map<String, String> prepareReplacements(RevalidationSession session) {
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

    /**
     * Wyszukuje i podmienia znaczniki w całym dokumencie Word (akapity, tabele, nagłówki, stopki).
     */
    private void replacePlaceholders(XWPFDocument doc, Map<String, String> replacements) {
        // Podmiana w głównych akapitach tekstu
        for (XWPFParagraph p : doc.getParagraphs()) {
            replaceInParagraph(p, replacements);
        }

        // Podmiana w tabelach i komórkach tabel
        for (XWPFTable table : doc.getTables()) {
            replaceInTable(table, replacements);
        }

        // Podmiana w nagłówkach
        for (XWPFHeader header : doc.getHeaderList()) {
            for (XWPFParagraph p : header.getParagraphs()) {
                replaceInParagraph(p, replacements);
            }
            for (XWPFTable table : header.getTables()) {
                replaceInTable(table, replacements);
            }
        }

        // Podmiana w stopkach
        for (XWPFFooter footer : doc.getFooterList()) {
            for (XWPFParagraph p : footer.getParagraphs()) {
                replaceInParagraph(p, replacements);
            }
            for (XWPFTable table : footer.getTables()) {
                replaceInTable(table, replacements);
            }
        }
    }

    /**
     * Przeszukuje i podmienia tekst w komórkach tabeli (w tym zagnieżdżonych).
     */
    private void replaceInTable(XWPFTable table, Map<String, String> replacements) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, replacements);
                }
                // Rekurencja dla zagnieżdżonych tabel w komórce
                for (XWPFTable nestedTable : cell.getTables()) {
                    replaceInTable(nestedTable, replacements);
                }
            }
        }
    }

    /**
     * Realizuje podmianę znaczników w pojedynczym akapicie, zabezpieczając przed rozbijaniem 
     * tokenów przez formatowanie MS Word (Split Run Solution).
     */
    private void replaceInParagraph(XWPFParagraph p, Map<String, String> replacements) {
        String text = p.getText();
        if (text == null || (!text.contains("$") && !text.contains("%"))) {
            return;
        }

        boolean updated = false;
        String newText = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String placeholder = entry.getKey();
            String replacement = entry.getValue();
            if (replacement == null) {
                continue; // Pomijamy wpisy z niezdefiniowaną wartością
            }
            if (newText.contains(placeholder)) {
                newText = newText.replace(placeholder, replacement);
                updated = true;
            }
        }

        if (updated) {
            // Usuwamy wszystkie dotychczasowe podfragmenty (runs) za wyjątkiem pierwszego
            int runSize = p.getRuns().size();
            for (int i = runSize - 1; i > 0; i--) {
                p.removeRun(i);
            }
            
            // Wpisujemy scalony, podmieniony tekst do pierwszego runu (lub tworzymy go, jeśli nie było)
            if (!p.getRuns().isEmpty()) {
                p.getRuns().get(0).setText(newText, 0);
            } else {
                p.createRun().setText(newText);
            }
        }
    }

    /**
     * Uzupełnia szablon Załącznika nr 3 danymi z sesji rewalidacji i zapisuje go do wskazanego strumienia wyjściowego.
     *
     * @param session      Sesja rewalidacji zawierająca dane urządzenia i serii pomiarowych
     * @param outputStream Strumień, do którego zostanie zapisany wyjściowy plik DOCX
     * @throws Exception W przypadku błędu wejścia/wyjścia lub parsowania dokumentu Word
     */
    public void generateAppendix3(RevalidationSession session, OutputStream outputStream) throws Exception {
        log.info("Rozpoczęcie generowania Załącznika nr 3 (Word DOCX) dla urządzenia: {}", 
                session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "NULL");

        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_3_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Nie odnaleziono szablonu Załącznika nr 3 pod ścieżką w zasobach: " + TEMPLATE_3_PATH);
            }

            try (XWPFDocument doc = new XWPFDocument(is)) {
                Map<String, String> replacements = prepareAppendix3Replacements(session);

                replacePlaceholders(doc, replacements);

                doc.write(outputStream);
                log.info("Załącznik nr 3 został pomyślnie wygenerowany i zapisany.");
            }
        }
    }

    /**
     * Przygotowuje mapę podmian dla wszystkich znaczników w Załączniku nr 3.
     */
    private Map<String, String> prepareAppendix3Replacements(RevalidationSession session) {
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

    private void populateRpwPlaceholders(Map<String, String> replacements, RevalidationSession session) {
        String nrRpw = "—";
        String skrotPracowni = "—";
        String rokRpw = "—";

        if (session.getCoolingDevice() != null) {
            CoolingDevice device = session.getCoolingDevice();
            if (device.getLaboratory() != null && device.getLaboratory().getAbbreviation() != null) {
                skrotPracowni = device.getLaboratory().getAbbreviation();
            } else if (device.getDepartment() != null && device.getDepartment().getAbbreviation() != null) {
                skrotPracowni = device.getDepartment().getAbbreviation();
            }

            if (validationPlanNumberRepository != null) {
                List<ValidationPlanNumber> planNumbers = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(device);
                if (planNumbers != null && !planNumbers.isEmpty()) {
                    ValidationPlanNumber activePlan = planNumbers.get(0);
                    if (activePlan.getPlanNumber() != null) {
                        nrRpw = String.valueOf(activePlan.getPlanNumber());
                    }
                    if (activePlan.getYear() != null) {
                        rokRpw = String.valueOf(activePlan.getYear());
                    }
                    if (activePlan.getCoolingDevice() != null) {
                        CoolingDevice activeDevice = activePlan.getCoolingDevice();
                        if (activeDevice.getLaboratory() != null && activeDevice.getLaboratory().getAbbreviation() != null) {
                            skrotPracowni = activeDevice.getLaboratory().getAbbreviation();
                        } else if (activeDevice.getDepartment() != null && activeDevice.getDepartment().getAbbreviation() != null) {
                            skrotPracowni = activeDevice.getDepartment().getAbbreviation();
                        }
                    }
                }
            }
        }

        if (skrotPracowni == null || skrotPracowni.trim().isEmpty()) {
            skrotPracowni = "—";
        }

        replacements.put("$NrRPW$", nrRpw);
        replacements.put("$skrotPracowni$", skrotPracowni);
        replacements.put("$rokRPW$", rokRpw);
    }

    /**
     * Uzupełnia szablon Załącznika nr 7 danymi z sesji rewalidacji (mapowania) i zapisuje go do wskazanego strumienia wyjściowego.
     *
     * @param session      Sesja rewalidacji zawierająca dane urządzenia i serii pomiarowych
     * @param outputStream Strumień, do którego zostanie zapisany wyjściowy plik DOCX
     * @throws Exception W przypadku błędu wejścia/wyjścia lub parsowania dokumentu Word
     */
    public void generateAppendix7(RevalidationSession session, OutputStream outputStream) throws Exception {
        log.info("Rozpoczęcie generowania Załącznika nr 7 (Word DOCX) dla urządzenia: {}", 
                session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "NULL");

        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_7_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Nie odnaleziono szablonu Załącznika nr 7 pod ścieżką w zasobach: " + TEMPLATE_7_PATH);
            }

            try (XWPFDocument doc = new XWPFDocument(is)) {
                Map<String, String> replacements = prepareAppendix7Replacements(session);

                replacePlaceholders(doc, replacements);

                doc.write(outputStream);
                log.info("Załącznik nr 7 został pomyślnie wygenerowany i zapisany.");
            }
        }
    }

    /**
     * Przygotowuje mapę podmian dla wszystkich znaczników w Załączniku nr 7.
     */
    private Map<String, String> prepareAppendix7Replacements(RevalidationSession session) {
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
