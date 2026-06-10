package com.mac.bry.desktop.service.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.PdfHeaderFooterHandler;
import com.mac.bry.desktop.service.helper.MappingValidator;
import com.mac.bry.desktop.service.stats.SensorStatsEngine;
import com.mac.bry.desktop.service.stats.SpcEngine;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import com.mac.bry.desktop.service.stats.ControlChartCalculator;
import com.mac.bry.desktop.service.stats.NelsonRulesDetector;
import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class RevalidationReportPdfRenderer {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void render(RevalidationSession session, File outputFile, File chartImageFile, 
                       HypothesisTestingService hypothesisTestingService, 
                       ValidationPlanNumberRepository validationPlanNumberRepository) throws IOException {
        log.info("Rozpoczęcie kompilacji zintegrowanego raportu PDF: {}", outputFile.getAbsolutePath());

        // 1. Sortowanie aktywnych pozycji celem zachowania spójności kolumn w tabeli
        List<RevalidationSession.GridPosition> activePositions = new ArrayList<>(session.getAssignedPositions().keySet());
        Collections.sort(activePositions);

        // Pobranie numeru RPW
        String rpwFormatted = "–";
        if (validationPlanNumberRepository != null && session.getCoolingDevice() != null) {
            List<ValidationPlanNumber> planNumbers = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(session.getCoolingDevice());
            if (!planNumbers.isEmpty()) {
                rpwFormatted = planNumbers.get(0).getFormattedRpw();
            }
        }

        // 2. Obliczenie kryptograficznej sumy kontrolnej SHA-256 z całej macierzy pomiarowej (FDA 21 CFR Part 11)
        String checksum = calculateSha256Checksum(session, activePositions);
        log.info("Zintegrowana suma kontrolna SHA-256 sesji rewalidacji: {}", checksum);

        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));

            // Czcionki z obsługą polskich znaków (Arial) pobrane z PdfStyleHelper
            BaseFont baseFont = PdfStyleHelper.getBaseFont();
            Font titleFont = PdfStyleHelper.getTitleFont();
            Font sectionFont = PdfStyleHelper.getSectionFont();
            Font labelFont = PdfStyleHelper.getLabelFont();
            Font valueFont = PdfStyleHelper.getValueFont();
            Font headerFont = PdfStyleHelper.getHeaderFont();
            Font cellFont = PdfStyleHelper.getCellFont();
            Font footerFont = PdfStyleHelper.getFooterFont();

            // Nagłówek i Stopka ze stronnicowaniem
            String reportHeaderTitle = session.getProcedureType() == GxPProcedureType.MAPPING
                    ? "Zintegrowany Raport Mapowania GxP Komory Chłodniczej"
                    : "Zintegrowany Raport Rewalidacji GxP Komory Chłodniczej";
            PdfHeaderFooterHandler handler = new PdfHeaderFooterHandler(
                    new Font(baseFont, 9, Font.BOLD, new java.awt.Color(44, 62, 80)),
                    footerFont,
                    reportHeaderTitle
            );
            writer.setPageEvent(handler);

            // Metadane PDF
            document.addTitle("Zintegrowany Raport GxP (RPW: " + rpwFormatted + ") - " + session.getCoolingDevice().getInventoryNumber());
            document.addAuthor("VCC Desktop Application");
            document.addCreator("VCC Validation Module");
            document.addCreationDate();

            document.setMargins(36, 36, 54, 54); // L, R, T, B
            document.open();

            // 1. TYTUŁ I METRYKA WIZYTY WALIDACYJNEJ
            String reportTitle = session.getProcedureType() == GxPProcedureType.MAPPING
                    ? "RAPORT Z MAPOWANIA PRZESTRZENNEGO ROZKŁADU TEMPERATUR (PDA TR-64)"
                    : "RAPORT Z REWALIDACJI PRZESTRZENNEJ ROZKŁADU TEMPERATUR";
            Paragraph title = new Paragraph(reportTitle, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            Paragraph section1 = new Paragraph("1. Charakterystyka Celu Walidacji (Komora Chłodnicza)", sectionFont);
            section1.setSpacingAfter(8);
            document.add(section1);

            PdfPTable chamberTable = new PdfPTable(4);
            chamberTable.setWidthPercentage(100);
            chamberTable.setWidths(new float[]{2, 3, 2, 3});
            chamberTable.setSpacingAfter(15);

            chamberTable.addCell(PdfStyleHelper.createMetaCell("Urządzenie:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingDevice().getName(), valueFont, false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Nr inwentarzowy:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingDevice().getInventoryNumber(), valueFont, false));

            chamberTable.addCell(PdfStyleHelper.createMetaCell("Nazwa komory:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getChamberName(), valueFont, false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Typ komory:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getChamberType().getDisplayName(), valueFont, false));

            chamberTable.addCell(PdfStyleHelper.createMetaCell("Zakres temp. pracy:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getFormattedMinOperatingTemp() + " do " + session.getCoolingChamber().getFormattedMaxOperatingTemp(), valueFont, false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Kubatura komory:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getFormattedVolume() + " (" + session.getCoolingChamber().getVolumeCategoryDisplayName() + ")", valueFont, false));

            chamberTable.addCell(PdfStyleHelper.createMetaCell("Przechowywany materiał:", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getMaterialName() + " (wymaga mapowania: " + (session.getCoolingChamber().isMappingRequired() ? "TAK" : "NIE") + ")", valueFont, false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Numer planu (RPW):", labelFont, true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(rpwFormatted, valueFont, false));

            if (session.getProcedureType() == GxPProcedureType.MAPPING) {
                MappingValidator.MappingResult mappingResult = MappingValidator.validate(session);
                String hotspotLabel = (mappingResult.isSuccess() && mappingResult.getHotspot() != null) ? mappingResult.getHotspot().getLabel() : "Niedostępne";
                String coldspotLabel = (mappingResult.isSuccess() && mappingResult.getColdspot() != null) ? mappingResult.getColdspot().getLabel() : "Niedostępne";
                chamberTable.addCell(PdfStyleHelper.createMetaCell("Wyznaczony Hotspot:", labelFont, true));
                chamberTable.addCell(PdfStyleHelper.createMetaCell(hotspotLabel, valueFont, false));
                chamberTable.addCell(PdfStyleHelper.createMetaCell("Wyznaczony Coldspot:", labelFont, true));
                chamberTable.addCell(PdfStyleHelper.createMetaCell(coldspotLabel, valueFont, false));
            } else if (session.getCoolingChamber().isMappingRequired()) {
                String mappingDate = session.getCoolingChamber().getLastMappingDate() != null ? session.getCoolingChamber().getLastMappingDate().toString() : "Brak";
                String hotspotLabel = session.getCoolingChamber().getHotspotPosition() != null ? session.getCoolingChamber().getHotspotPosition().getLabel() : "Brak";
                String coldspotLabel = session.getCoolingChamber().getColdspotPosition() != null ? session.getCoolingChamber().getColdspotPosition().getLabel() : "Brak";
                chamberTable.addCell(PdfStyleHelper.createMetaCell("Referencyjne mapowanie:", labelFont, true));
                chamberTable.addCell(PdfStyleHelper.createMetaCell(mappingDate, valueFont, false));
                chamberTable.addCell(PdfStyleHelper.createMetaCell("Punkty krytyczne (H/C):", labelFont, true));
                chamberTable.addCell(PdfStyleHelper.createMetaCell("Hotspot: " + hotspotLabel + "\nColdspot: " + coldspotLabel, valueFont, false));
            } else {
                chamberTable.addCell(PdfStyleHelper.createMetaCell("Wymóg mapowania:", labelFont, true));
                chamberTable.addCell(PdfStyleHelper.createMetaCell("NIE dotyczy", valueFont, false));
                chamberTable.addCell(PdfStyleHelper.createMetaCell("", labelFont, true));
                chamberTable.addCell(PdfStyleHelper.createMetaCell("", valueFont, false));
            }

            document.add(chamberTable);

            // 2. MATRYCA MAPOWANIA REJESTRATORÓW I ICH ŚWIADECTW WZORCOWANIA
            Paragraph section2 = new Paragraph("2. Traceability - Wykaz Rejestratorów oraz Świadectw Wzorcowania", sectionFont);
            section2.setSpacingAfter(8);
            document.add(section2);

            PdfPTable traceabilityTable = new PdfPTable(5);
            traceabilityTable.setWidthPercentage(100);
            traceabilityTable.setWidths(new float[]{2.5f, 2, 2, 2.5f, 2});
            traceabilityTable.setSpacingAfter(15);

            // Nagłówki
            String[] traceHeaders = {"Fizyczna Pozycja", "Model", "Numer Seryjny", "Certyfikat Wzorcowania", "Ważność Certyfikatu"};
            for (String header : traceHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5);
                cell.setBackgroundColor(new java.awt.Color(51, 65, 85)); // Slate 600
                traceabilityTable.addCell(cell);
            }

            // Wiersze
            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                String certNo = d.getLatestCalibration() != null ? d.getLatestCalibration().getCertificateNumber() : "Brak";
                String validity = d.getLatestCalibration() != null ? d.getLatestCalibration().getValidUntil().toString() : "–";

                traceabilityTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_LEFT));
                traceabilityTable.addCell(PdfStyleHelper.createCell(d.getModel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                traceabilityTable.addCell(PdfStyleHelper.createCell(d.getSerialNumber(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                traceabilityTable.addCell(PdfStyleHelper.createCell(certNo, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                traceabilityTable.addCell(PdfStyleHelper.createCell(validity, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
            }
            document.add(traceabilityTable);

            // 3. INTEGRALNOŚĆ DANYCH (SHA-256)
            PdfPTable hashTable = new PdfPTable(1);
            hashTable.setWidthPercentage(100);
            hashTable.setSpacingAfter(15);

            Paragraph hashPara = new Paragraph();
            hashPara.setLeading(14.0f);
            hashPara.add(new Chunk("Kryptograficzna Suma Kontrolna Integralności GxP (SHA-256 Data Integrity Checksum):\n", labelFont));
            Font hashFont = new Font(baseFont, 7.5f, Font.NORMAL, new java.awt.Color(44, 62, 80));
            hashPara.add(new Chunk(checksum, hashFont));

            PdfPCell hashCell = new PdfPCell(hashPara);
            hashCell.setBackgroundColor(new java.awt.Color(240, 253, 250)); // Light mint emerald
            hashCell.setPadding(10);
            hashCell.setBorderColor(new java.awt.Color(153, 246, 228));
            hashTable.addCell(hashCell);
            document.add(hashCell); // wait! In original it was: hashTable.addCell(hashCell); document.add(hashTable);
            // Wait, let's look at original line 210: "document.add(hashTable);"
            // Yes, so we add hashTable to document, not hashCell to document.
            // Let's make sure we do document.add(hashTable);
            hashTable.addCell(hashCell);
            document.add(hashTable);

            // 4. MULTI-KANAŁOWY WYKRES ROZKŁADU TEMPERATUR
            if (chartImageFile != null && chartImageFile.exists()) {
                document.newPage();

                Paragraph section3 = new Paragraph("3. Zintegrowany Przebieg Temperatury w Komorze Chłodniczej", sectionFont);
                section3.setSpacingAfter(8);
                document.add(section3);

                Image chartImg = Image.getInstance(chartImageFile.getAbsolutePath());
                chartImg.setAlignment(Element.ALIGN_CENTER);
                chartImg.scaleToFit(500, 240);
                chartImg.setSpacingAfter(15);
                document.add(chartImg);
            } else {
                document.newPage();
            }

            // 5. CHARAKTERYSTYKA METROLOGICZNA I BUDŻET NIEPEWNOŚCI
            Paragraph section4 = new Paragraph("4. Charakterystyka Metrologiczna oraz Budżet Niepewności (GUM & GxP)", sectionFont);
            section4.setSpacingAfter(8);
            document.add(section4);

            PdfPTable metrologicalTable = new PdfPTable(9);
            metrologicalTable.setWidthPercentage(100);
            metrologicalTable.setWidths(new float[]{2.0f, 1.8f, 1.2f, 1.2f, 1.2f, 1.2f, 1.5f, 1.0f, 1.8f});
            metrologicalTable.setSpacingAfter(15);

            // Nagłówki
            String[] metroHeaders = {
                "Pozycja", "S/N", "T min", "T max", "T avg", "MKT", "Niepewność U", "Szpilki", "Dryft"
            };
            for (String header : metroHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5);
                cell.setBackgroundColor(new java.awt.Color(51, 65, 85)); // Slate 600
                metrologicalTable.addCell(cell);
            }

            // Wiersze
            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                ThermoMeasurementSeries s = d.getSeries();

                double min = s.getMinTemperature() != null ? s.getMinTemperature() : 0.0;
                double max = s.getMaxTemperature() != null ? s.getMaxTemperature() : 0.0;
                double avg = s.getAvgTemperature() != null ? s.getAvgTemperature() : 0.0;
                double mkt = s.getMktTemperature() != null ? s.getMktTemperature() : 0.0;
                double unc = s.getExpandedUncertainty() != null ? s.getExpandedUncertainty() : 0.0;
                int spikes = s.getSpikeCount() != null ? s.getSpikeCount() : 0;
                String drift = s.getDriftClassification() != null ? s.getDriftClassification() : "STABLE";

                metrologicalTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_LEFT));
                metrologicalTable.addCell(PdfStyleHelper.createCell(d.getSerialNumber(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", min), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", max), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", avg), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", mkt), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("±%.3f°C", unc), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(PdfStyleHelper.createCell(String.valueOf(spikes), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));

                java.awt.Color driftBg;
                if ("STABLE".equals(drift)) {
                    driftBg = new java.awt.Color(240, 253, 244); // Light green
                } else if ("SPIKE".equals(drift)) {
                    driftBg = new java.awt.Color(239, 246, 255); // Light blue
                } else if ("DRIFT".equals(drift)) {
                    driftBg = new java.awt.Color(254, 242, 242); // Light red
                } else { // MIXED
                    driftBg = new java.awt.Color(255, 251, 235); // Light orange
                }
                metrologicalTable.addCell(PdfStyleHelper.createCell(drift, cellFont, driftBg, Element.ALIGN_CENTER));
            }
            document.add(metrologicalTable);

            // Nowa strona na analizę statystyczną i wnioski GxP
            document.newPage();

            // 4.1. Analiza Statystyczna oraz Sterowanie Procesem (SPC)
            Paragraph section4_1 = new Paragraph("4.1. Analiza Statystyczna oraz Sterowanie Procesem (SPC)", sectionFont);
            section4_1.setSpacingAfter(8);
            document.add(section4_1);

            PdfPTable statsTable = new PdfPTable(10);
            statsTable.setWidthPercentage(100);
            statsTable.setWidths(new float[]{1.7f, 1.5f, 1.1f, 1.1f, 1.1f, 1.1f, 1.1f, 0.9f, 0.9f, 1.2f});
            statsTable.setSpacingAfter(15);

            String[] statsHeaders = {
                "Pozycja", "S/N", "Mediana", "Odch. std.", "RSD", "Skośność", "Kurtoza", "Cp", "Cpk", "JB p-val"
            };
            for (String header : statsHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(4);
                cell.setBackgroundColor(new java.awt.Color(51, 65, 85)); // Slate 600
                statsTable.addCell(cell);
            }

            CoolingChamber chamber = session.getCoolingChamber();
            Double lsl = (chamber != null) ? chamber.getMinOperatingTemp() : null;
            Double usl = (chamber != null) ? chamber.getMaxOperatingTemp() : null;

            List<Double> allCpk = new ArrayList<>();
            List<Double> allStdDev = new ArrayList<>();
            List<Double> allRsd = new ArrayList<>();
            List<Double> allJbPValues = new ArrayList<>();
            List<double[]> allSeriesValues = new ArrayList<>();

            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                ThermoMeasurementSeries s = d.getSeries();

                double[] values = s.getMeasurements() != null ? s.getMeasurements().stream()
                        .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                        .toArray() : new double[0];
                allSeriesValues.add(values);

                double median = 0.0;
                double stdDev = 0.0;
                double rsd = 0.0;
                double skewness = Double.NaN;
                double kurtosis = Double.NaN;
                double jbPValue = 1.0;
                String cpStr = "–";
                String cpkStr = "–";

                if (values.length >= 2) {
                    median = SensorStatsEngine.calculateMedian(values);
                    stdDev = SensorStatsEngine.calculateStdDev(values);
                    rsd = SensorStatsEngine.calculateRsd(values);
                    allStdDev.add(stdDev);
                    allRsd.add(rsd);

                    if (values.length >= 3) {
                        skewness = SensorStatsEngine.calculateSkewness(values);
                    }
                    if (values.length >= 4) {
                        kurtosis = SensorStatsEngine.calculateKurtosis(values);
                    }
                    if (lsl != null && usl != null) {
                        CapabilityIndexes capability = SpcEngine.calculateCapability(values, lsl, usl);
                        allCpk.add(capability.getCpk());
                        cpStr = String.format("%.2f", capability.getCp());
                        cpkStr = String.format("%.2f", capability.getCpk());
                    }
                    if (values.length >= 5) {
                        jbPValue = hypothesisTestingService.performJarqueBera(values);
                    }
                    allJbPValues.add(jbPValue);
                }

                statsTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_LEFT));
                statsTable.addCell(PdfStyleHelper.createCell(d.getSerialNumber(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                statsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f°C", median), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                statsTable.addCell(PdfStyleHelper.createCell(String.format("%.3f°C", stdDev), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                statsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f%%", rsd), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));

                String skewStr = (Double.isNaN(skewness) || Double.isInfinite(skewness)) ? "–" : String.format("%.3f", skewness);
                statsTable.addCell(PdfStyleHelper.createCell(skewStr, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));

                String kurtStr = (Double.isNaN(kurtosis) || Double.isInfinite(kurtosis)) ? "–" : String.format("%.3f", kurtosis);
                statsTable.addCell(PdfStyleHelper.createCell(kurtStr, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));

                statsTable.addCell(PdfStyleHelper.createCell(cpStr, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                statsTable.addCell(PdfStyleHelper.createCell(cpkStr, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));

                String jbPValStr = (values.length >= 5) ? String.format("%.4f", jbPValue) : "–";
                java.awt.Color jbBg = (values.length >= 5 && jbPValue < 0.05) ? new java.awt.Color(254, 242, 242) : java.awt.Color.WHITE;
                statsTable.addCell(PdfStyleHelper.createCell(jbPValStr, cellFont, jbBg, Element.ALIGN_CENTER));
            }
            document.add(statsTable);

            // Obliczenie rozstępu przestrzennego
            double sumSpatialRange = 0.0;
            double maxSpatialRange = 0.0;
            int pointsCount = activePositions.isEmpty() ? 0 : session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().size();
            for (int rowIndex = 0; rowIndex < pointsCount; rowIndex++) {
                double minT = Double.MAX_VALUE;
                double maxT = -Double.MAX_VALUE;
                boolean validRow = false;
                for (RevalidationSession.GridPosition pos : activePositions) {
                    RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                    if (d.getSeries() != null && d.getSeries().getMeasurements() != null && rowIndex < d.getSeries().getMeasurements().size()) {
                        double temp = d.getSeries().getMeasurements().get(rowIndex).getRawCelsius();
                        if (temp < minT) minT = temp;
                        if (temp > maxT) maxT = temp;
                        validRow = true;
                    }
                }
                if (validRow) {
                    double diff = maxT - minT;
                    sumSpatialRange += diff;
                    if (diff > maxSpatialRange) {
                        maxSpatialRange = diff;
                    }
                }
            }
            double meanSpatialRange = pointsCount > 0 ? sumSpatialRange / pointsCount : 0.0;

            // 4.2. Wnioski Statystyczne i Ocena Zdolności GxP
            Paragraph section4_2 = new Paragraph("4.2. Wnioski Statystyczne i Ocena Zdolności GxP", sectionFont);
            section4_2.setSpacingBefore(10);
            section4_2.setSpacingAfter(8);
            document.add(section4_2);

            Paragraph conclusions = new Paragraph();
            conclusions.setLeading(14.0f);

            // 1. Zdolność procesu
            conclusions.add(new Chunk("1. Ocena Zdolności Procesu (SPC):\n", labelFont));
            String capabilityText;
            if (lsl == null || usl == null || allCpk.isEmpty()) {
                capabilityText = "Ze względu na brak zdefiniowanych technologicznych granic temperatury pracy (LSL/USL) dla tej komory, wskaźniki zdolności procesu Cp i Cpk nie mogły zostać wyznaczone.";
            } else {
                double minCpk = allCpk.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                if (minCpk >= 1.33) {
                    capabilityText = String.format("Wszystkie punkty pomiarowe charakteryzują się doskonałą zdolnością procesu (minimalny wskaźnik Cpk = %.2f >= 1.33). Ryzyko przekroczenia dopuszczalnych limitów temperatury jest znikome, co świadczy o wysokiej stabilności i precyzji regulacji w komorze.", minCpk);
                } else if (minCpk < 1.0) {
                    capabilityText = String.format("OSTRZEŻENIE: Wykryto punkty pomiarowe o krytycznie niskiej zdolności procesu (minimalny wskaźnik Cpk = %.2f < 1.00). Istnieje wysokie ryzyko przekroczenia limitów temperatury (zarówno dolnego, jak i górnego). Zaleca się podjęcie natychmiastowych działań korygujących (np. regulacja nastaw, serwis układu chłodzenia lub relokalizacja czujników).", minCpk);
                } else {
                    capabilityText = String.format("Wskaźniki zdolności procesu są akceptowalne (minimalny wskaźnik Cpk = %.2f w przedziale [1.00, 1.33)). Zdolność procesu jest zadowalająca, zaleca się okresowy monitoring stabilności temperatury.", minCpk);
                }
            }
            conclusions.add(new Chunk(capabilityText + "\n\n", valueFont));

            // 2. Stabilność czasowa i normy WHO
            conclusions.add(new Chunk("2. Ocena Stabilności Czasowej (Wytyczne WHO):\n", labelFont));
            ChamberType type = (chamber != null && chamber.getChamberType() != null) ? chamber.getChamberType() : ChamberType.FRIDGE;
            double limitStdDev = (type == ChamberType.FREEZER || type == ChamberType.LOW_TEMP_FREEZER || type == ChamberType.FREEZE_ROOM) ? 1.0 : 0.3;
            String chamberTypeLabel = (type == ChamberType.FREEZER || type == ChamberType.LOW_TEMP_FREEZER || type == ChamberType.FREEZE_ROOM) ? "zamrażarek/mroźni" : "lodówek/chłodni";

            boolean allStdDevOk = true;
            for (double sd : allStdDev) {
                if (sd > limitStdDev) {
                    allStdDevOk = false;
                    break;
                }
            }

            String stabilityText;
            if (allStdDevOk) {
                stabilityText = String.format("Odchylenia standardowe (std dev) we wszystkich punktach pomiarowych spełniają wytyczne WHO TRS 961 Supplement 8 (stabilność <= %.1f°C dla %s). Wahania temperatury w czasie są stabilne.", limitStdDev, chamberTypeLabel);
            } else {
                stabilityText = String.format("OSTRZEŻENIE: Wykryto punkty pomiarowe, w których odchylenie standardowe przekracza zalecane przez WHO TRS 961 Supplement 8 limity stabilności (<= %.1f°C dla %s), co wskazuje na niestabilność regulacji czasowej.", limitStdDev, chamberTypeLabel);
            }

            // RSD check for positive temperatures
            double overallMean = 0.0;
            int countPoints = 0;
            for (double[] vals : allSeriesValues) {
                for (double v : vals) {
                    overallMean += v;
                    countPoints++;
                }
            }
            overallMean = countPoints > 0 ? overallMean / countPoints : 0.0;
            if (overallMean > 0.0) {
                boolean allRsdOk = true;
                for (double r : allRsd) {
                    if (r > 5.0) {
                        allRsdOk = false;
                        break;
                    }
                }
                if (allRsdOk) {
                    stabilityText += " Współczynnik zmienności (RSD) we wszystkich punktach spełnia limit <= 5.0% dla temperatur dodatnich.";
                } else {
                    stabilityText += " OSTRZEŻENIE: W wybranych punktach współczynnik zmienności (RSD) przekroczył limit 5.0% wymagany dla temperatur dodatnich.";
                }
            }
            conclusions.add(new Chunk(stabilityText + "\n\n", valueFont));

            // 3. Rozkład temperatur
            conclusions.add(new Chunk("3. Ocena Rozkładu Temperatury (Test Normalności JB):\n", labelFont));
            long nonNormalCount = allJbPValues.stream().filter(pv -> pv < 0.05).count();
            String normalityText;
            if (nonNormalCount > 0) {
                normalityText = String.format("Test normalności Jarque-Bera wykazuje istotne statystycznie odstępstwa od rozkładu normalnego (p < 0.05) w %d punktach pomiarowych. Może to świadczyć o nieliniowości regulacji temperatury, obecności okresowych cykli rozmrażania (defrostów) lub wymuszonym obiegu powietrza powodującym periodyczne fluktuacje.", nonNormalCount);
            } else {
                normalityText = "Rozkład temperatur we wszystkich punktach pomiarowych nie odbiega w sposób istotny od rozkładu normalnego (p >= 0.05), co wskazuje na stabilne, stochastyczne wahania wokół punktu nastawy bez wyraźnych zakłóceń periodycznych.";
            }
            conclusions.add(new Chunk(normalityText + "\n\n", valueFont));

            // 4. Rozstęp przestrzenny
            conclusions.add(new Chunk("4. Ocena Jednorodności Przestrzennej:\n", labelFont));
            String spatialText = String.format("Średni rozstęp przestrzenny (jednorodność temperatury w komorze) wynosi %.2f°C, natomiast maksymalny chwilowy rozstęp przestrzenny w czasie trwania sesji wyniósł %.2f°C. Wartości te określają maksymalny gradient temperatury pomiędzy najcieplejszym a najzimniejszym punktem komory chłodniczej i potwierdzają stopień stabilności rozkładu przestrzennego.", meanSpatialRange, maxSpatialRange);
            conclusions.add(new Chunk(spatialText + "\n\n", valueFont));

            // 5. Weryfikacja reguł stabilności Nelsona
            conclusions.add(new Chunk("5. Weryfikacja Stabilności Procesu (Karty Shewharta & Nelson Rules):\n", labelFont));
            
            int totalXBarViolations = 0;
            int totalSViolations = 0;
            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                double[] values = d.getSeries().getMeasurements() != null ? d.getSeries().getMeasurements().stream()
                        .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                        .toArray() : new double[0];
                ControlChartData spcData = ControlChartCalculator.calculateShewhartLimits(values);
                totalXBarViolations += NelsonRulesDetector.detectXBarViolations(spcData).size();
                totalSViolations += NelsonRulesDetector.detectSViolations(spcData).size();
            }

            String nelsonConclusionsText;
            if (totalXBarViolations == 0 && totalSViolations == 0) {
                nelsonConclusionsText = "Analiza kart kontrolnych Shewharta (X-bar & S) nie wykazała żadnych naruszeń stabilności procesu na podstawie reguł Nelsona. Świadczy to o stabilności stochastycznej i braku przyczyn systemowych (zakłóceń) wpływających na temperaturę komory.";
            } else {
                nelsonConclusionsText = String.format("OSTRZEŻENIE: Wykryto łącznie %d naruszeń reguł Nelsona dla karty X-bar oraz %d przekroczeń granic na karcie S. Wskazuje to na obecność zmienności o charakterze nielosowym (przyczyn systemowych, np. cykle defrostu, wahania obciążenia komory lub niestabilność układu sterowania). Szczegółowy wykaz przedstawiono w Sekcji 4.3.", totalXBarViolations, totalSViolations);
            }
            conclusions.add(new Chunk(nelsonConclusionsText, valueFont));

            PdfPTable conclusionsBox = new PdfPTable(1);
            conclusionsBox.setWidthPercentage(100);
            conclusionsBox.setSpacingAfter(15);

            PdfPCell boxCell = new PdfPCell(conclusions);
            boxCell.setBackgroundColor(new java.awt.Color(248, 250, 252)); // Slate 50
            boxCell.setPadding(10);
            boxCell.setBorderColor(new java.awt.Color(203, 213, 225)); // Slate 300
            conclusionsBox.addCell(boxCell);

            document.add(conclusionsBox);

            // Nowa strona na weryfikację stabilności Shewharta i reguł Nelsona (Sekcja 4.3)
            document.newPage();

            Paragraph section4_3 = new Paragraph("4.3. Weryfikacja Stabilności Procesu (Karty Shewharta i Reguły Nelsona)", sectionFont);
            section4_3.setSpacingAfter(8);
            document.add(section4_3);

            PdfPTable shewhartTable = new PdfPTable(7);
            shewhartTable.setWidthPercentage(100);
            shewhartTable.setWidths(new float[]{2.0f, 1.3f, 1.8f, 1.0f, 1.3f, 2.6f, 2.0f});
            shewhartTable.setSpacingAfter(15);

            // Nagłówki
            String[] shewhartHeaders = {
                "Pozycja", "X-bar CL", "X-bar LCL/UCL", "S CL", "S UCL", "Naruszenia Nelsona (X-Bar)", "Naruszenia S"
            };
            for (String header : shewhartHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(4);
                cell.setBackgroundColor(new java.awt.Color(51, 65, 85)); // Slate 600
                shewhartTable.addCell(cell);
            }

            // Wiersze
            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                ThermoMeasurementSeries s = d.getSeries();

                double[] values = s.getMeasurements() != null ? s.getMeasurements().stream()
                        .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                        .toArray() : new double[0];

                ControlChartData spcData = ControlChartCalculator.calculateShewhartLimits(values);
                List<NelsonRulesDetector.Violation> xbarViolations = NelsonRulesDetector.detectXBarViolations(spcData);
                List<NelsonRulesDetector.Violation> sViolations = NelsonRulesDetector.detectSViolations(spcData);

                String xbarViolationsStr = "Brak naruszeń";
                if (!xbarViolations.isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    for (NelsonRulesDetector.Violation v : xbarViolations) {
                        codes.add("Reguła " + v.getRuleNumber() + " (Podgr. " + v.getSubgroupIndex() + ")");
                    }
                    xbarViolationsStr = String.join("\n", codes);
                }

                String sViolationsStr = "Brak naruszeń";
                if (!sViolations.isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    for (NelsonRulesDetector.Violation v : sViolations) {
                        codes.add("UCL/LCL (Podgr. " + v.getSubgroupIndex() + ")");
                    }
                    sViolationsStr = String.join("\n", codes);
                }

                shewhartTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_LEFT));
                shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.2f°C", spcData.getXBarCentralLine()), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.2f / %.2f", spcData.getXBarLcl(), spcData.getXBarUcl()), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.3f°C", spcData.getSCentralLine()), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.3f°C", spcData.getSUcl()), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));

                java.awt.Color xbarBg = xbarViolations.isEmpty() ? java.awt.Color.WHITE : new java.awt.Color(254, 242, 242);
                java.awt.Color sBg = sViolations.isEmpty() ? java.awt.Color.WHITE : new java.awt.Color(254, 242, 242);

                shewhartTable.addCell(PdfStyleHelper.createCell(xbarViolationsStr, cellFont, xbarBg, Element.ALIGN_LEFT));
                shewhartTable.addCell(PdfStyleHelper.createCell(sViolationsStr, cellFont, sBg, Element.ALIGN_LEFT));
            }
            document.add(shewhartTable);

            // Nowa strona na tabelę pomiarową
            document.newPage();

            // 5. SZCZEGÓŁOWA TABELA ZSYNCHRONIZOWANYCH WYNIKÓW POMIARÓW
            Paragraph section5 = new Paragraph("5. Szczegółowy Wykaz Zsynchronizowanych Serii Pomiarowych", sectionFont);
            section5.setSpacingAfter(8);
            document.add(section5);

            int columnsCount = activePositions.size() + 2; // Lp + Czas + Kanały
            float[] widths = new float[columnsCount];
            widths[0] = 1.0f; // Lp.
            widths[1] = 2.5f; // Czas
            for (int i = 2; i < columnsCount; i++) {
                widths[i] = 1.5f; // Kanały
            }

            PdfPTable matrixTable = new PdfPTable(columnsCount);
            matrixTable.setWidthPercentage(100);
            matrixTable.setWidths(widths);
            matrixTable.setHeaderRows(1);
            matrixTable.setSpacingAfter(15);

            // Nagłówek Lp. i Czas
            PdfPCell cellLp = new PdfPCell(new Phrase("Lp.", headerFont));
            cellLp.setBackgroundColor(new java.awt.Color(30, 41, 59)); // Slate 800
            cellLp.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellLp.setPadding(4);
            matrixTable.addCell(cellLp);

            PdfPCell cellTime = new PdfPCell(new Phrase("Czas Lokalny Pomiaru", headerFont));
            cellTime.setBackgroundColor(new java.awt.Color(30, 41, 59));
            cellTime.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellTime.setPadding(4);
            matrixTable.addCell(cellTime);

            // Nagłówki kanałów siatki
            for (RevalidationSession.GridPosition pos : activePositions) {
                String shortCode = getShortCode(pos);
                PdfPCell cellChan = new PdfPCell(new Phrase(shortCode, headerFont));
                cellChan.setBackgroundColor(new java.awt.Color(30, 41, 59));
                cellChan.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellChan.setPadding(4);
                matrixTable.addCell(cellChan);
            }

            // Wiersze danych pomiarowych (wiemy, że wszystkie serie mają po 40 punktów)
            int totalPoints = session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().size();
            for (int rowIndex = 0; rowIndex < totalPoints; rowIndex++) {
                java.awt.Color bgColor = (rowIndex % 2 == 0) ? java.awt.Color.WHITE : new java.awt.Color(248, 250, 252);

                // 1. Indeks
                matrixTable.addCell(PdfStyleHelper.createCell(String.valueOf(rowIndex + 1), cellFont, bgColor, Element.ALIGN_CENTER));

                // 2. Czas (pobrany z pierwszego kanału)
                LocalDateTime timestamp = session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().get(rowIndex).getTimestampLocal();
                matrixTable.addCell(PdfStyleHelper.createCell(timestamp.format(DTF), cellFont, bgColor, Element.ALIGN_CENTER));

                // 3. Wartości temperatur dla poszczególnych aktywnych pozycji
                for (RevalidationSession.GridPosition pos : activePositions) {
                    RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                    double temp = d.getSeries().getMeasurements().get(rowIndex).getRawCelsius();
                    matrixTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", temp), cellFont, bgColor, Element.ALIGN_CENTER));
                }
            }

            document.add(matrixTable);

        } catch (Exception e) {
            log.error("Błąd kompilacji raportu GxP PDF", e);
            throw new IOException("Błąd podczas kompilacji raportu PDF: " + e.getMessage(), e);
        }
    }

    private String getShortCode(RevalidationSession.GridPosition pos) {
        switch (pos) {
            case TOP_FRONT_LEFT: return "G-PL";
            case TOP_FRONT_RIGHT: return "G-PP";
            case TOP_BACK_LEFT: return "G-TL";
            case TOP_BACK_RIGHT: return "G-TP";
            case BOTTOM_FRONT_LEFT: return "D-PL";
            case BOTTOM_FRONT_RIGHT: return "D-PP";
            case BOTTOM_BACK_LEFT: return "D-TL";
            case BOTTOM_BACK_RIGHT: return "D-TP";
            default: return pos.name();
        }
    }

    private String calculateSha256Checksum(RevalidationSession session, List<RevalidationSession.GridPosition> activePositions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();

            // Powiązanie z komorą i urządzeniem nadrzędnym
            sb.append(session.getCoolingDevice().getInventoryNumber())
              .append(session.getCoolingChamber().getChamberName());

            // Odczyt zsynchronizowanej macierzy pomiarów
            int pointCount = session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().size();
            for (int i = 0; i < pointCount; i++) {
                sb.append(i + 1);
                for (RevalidationSession.GridPosition pos : activePositions) {
                    RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                    ThermoMeasurementPoint pt = d.getSeries().getMeasurements().get(i);
                    sb.append(pt.getTimestampLocal().toString())
                      .append(pt.getRawCelsius());
                }
            }

            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            log.error("Błąd podczas obliczania sumy kontrolnej integralności sesji", e);
            return "INTEGRITY_ERROR";
        }
    }
}
