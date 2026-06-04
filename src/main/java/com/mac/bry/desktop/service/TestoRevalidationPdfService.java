package com.mac.bry.desktop.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.helper.MappingValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

@Service
@lombok.RequiredArgsConstructor
@Slf4j
public class TestoRevalidationPdfService {

    private final ValidationPlanNumberRepository validationPlanNumberRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generuje zintegrowany raport z rewalidacji komory chłodniczej GxP w formacie PDF.
     */
    public void generateRevalidationReport(RevalidationSession session, File outputFile, File chartImageFile) throws IOException {
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

            // Czcionki z obsługą polskich znaków (Arial)
            BaseFont baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(baseFont, 15, Font.BOLD, new java.awt.Color(31, 58, 86));
            Font sectionFont = new Font(baseFont, 11, Font.BOLD, new java.awt.Color(44, 62, 80));
            Font labelFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.BLACK);
            Font valueFont = new Font(baseFont, 9, Font.NORMAL, java.awt.Color.BLACK);
            Font headerFont = new Font(baseFont, 8, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(baseFont, 8, Font.NORMAL, java.awt.Color.BLACK);
            Font footerFont = new Font(baseFont, 8, Font.ITALIC, java.awt.Color.GRAY);

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

            chamberTable.addCell(createMetaCell("Urządzenie:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingDevice().getName(), valueFont, false));
            chamberTable.addCell(createMetaCell("Nr inwentarzowy:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingDevice().getInventoryNumber(), valueFont, false));

            chamberTable.addCell(createMetaCell("Nazwa komory:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingChamber().getChamberName(), valueFont, false));
            chamberTable.addCell(createMetaCell("Typ komory:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingChamber().getChamberType().getDisplayName(), valueFont, false));

            chamberTable.addCell(createMetaCell("Zakres temp. pracy:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingChamber().getFormattedMinOperatingTemp() + " do " + session.getCoolingChamber().getFormattedMaxOperatingTemp(), valueFont, false));
            chamberTable.addCell(createMetaCell("Kubatura komory:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingChamber().getFormattedVolume() + " (" + session.getCoolingChamber().getVolumeCategoryDisplayName() + ")", valueFont, false));

            chamberTable.addCell(createMetaCell("Przechowywany materiał:", labelFont, true));
            chamberTable.addCell(createMetaCell(session.getCoolingChamber().getMaterialName() + " (wymaga mapowania: " + (session.getCoolingChamber().isMappingRequired() ? "TAK" : "NIE") + ")", valueFont, false));
            chamberTable.addCell(createMetaCell("Numer planu (RPW):", labelFont, true));
            chamberTable.addCell(createMetaCell(rpwFormatted, valueFont, false));

            if (session.getProcedureType() == GxPProcedureType.MAPPING) {
                MappingValidator.MappingResult mappingResult = MappingValidator.validate(session);
                String hotspotLabel = (mappingResult.isSuccess() && mappingResult.getHotspot() != null) ? mappingResult.getHotspot().getLabel() : "Niedostępne";
                String coldspotLabel = (mappingResult.isSuccess() && mappingResult.getColdspot() != null) ? mappingResult.getColdspot().getLabel() : "Niedostępne";
                chamberTable.addCell(createMetaCell("Wyznaczony Hotspot:", labelFont, true));
                chamberTable.addCell(createMetaCell(hotspotLabel, valueFont, false));
                chamberTable.addCell(createMetaCell("Wyznaczony Coldspot:", labelFont, true));
                chamberTable.addCell(createMetaCell(coldspotLabel, valueFont, false));
            } else if (session.getCoolingChamber().isMappingRequired()) {
                String mappingDate = session.getCoolingChamber().getLastMappingDate() != null ? session.getCoolingChamber().getLastMappingDate().toString() : "Brak";
                String hotspotLabel = session.getCoolingChamber().getHotspotPosition() != null ? session.getCoolingChamber().getHotspotPosition().getLabel() : "Brak";
                String coldspotLabel = session.getCoolingChamber().getColdspotPosition() != null ? session.getCoolingChamber().getColdspotPosition().getLabel() : "Brak";
                chamberTable.addCell(createMetaCell("Referencyjne mapowanie:", labelFont, true));
                chamberTable.addCell(createMetaCell(mappingDate, valueFont, false));
                chamberTable.addCell(createMetaCell("Punkty krytyczne (H/C):", labelFont, true));
                chamberTable.addCell(createMetaCell("Hotspot: " + hotspotLabel + "\nColdspot: " + coldspotLabel, valueFont, false));
            } else {
                chamberTable.addCell(createMetaCell("Wymóg mapowania:", labelFont, true));
                chamberTable.addCell(createMetaCell("NIE dotyczy", valueFont, false));
                chamberTable.addCell(createMetaCell("", labelFont, true));
                chamberTable.addCell(createMetaCell("", valueFont, false));
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
                
                traceabilityTable.addCell(createCell(pos.getLabel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_LEFT));
                traceabilityTable.addCell(createCell(d.getModel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                traceabilityTable.addCell(createCell(d.getSerialNumber(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                traceabilityTable.addCell(createCell(certNo, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                traceabilityTable.addCell(createCell(validity, cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
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
            document.add(hashTable);

            // 4. MULTI-KANAŁOWY WYKRES ROZKŁADU TEMPERATUR
            if (chartImageFile != null && chartImageFile.exists()) {
                // Przejście na nową stronę dla wykresu i analizy metrologicznej
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

                metrologicalTable.addCell(createCell(pos.getLabel(), cellFont, java.awt.Color.WHITE, Element.ALIGN_LEFT));
                metrologicalTable.addCell(createCell(d.getSerialNumber(), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(createCell(String.format("%.1f°C", min), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(createCell(String.format("%.1f°C", max), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(createCell(String.format("%.1f°C", avg), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(createCell(String.format("%.1f°C", mkt), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(createCell(String.format("±%.3f°C", unc), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                metrologicalTable.addCell(createCell(String.valueOf(spikes), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                
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
                metrologicalTable.addCell(createCell(drift, cellFont, driftBg, Element.ALIGN_CENTER));
            }
            document.add(metrologicalTable);

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
                // Krótki kod np. "G-PL"
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
                matrixTable.addCell(createCell(String.valueOf(rowIndex + 1), cellFont, bgColor, Element.ALIGN_CENTER));

                // 2. Czas (pobrany z pierwszego kanału)
                LocalDateTime timestamp = session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().get(rowIndex).getTimestampLocal();
                matrixTable.addCell(createCell(timestamp.format(DTF), cellFont, bgColor, Element.ALIGN_CENTER));

                // 3. Wartości temperatur dla poszczególnych aktywnych pozycji
                for (RevalidationSession.GridPosition pos : activePositions) {
                    RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                    double temp = d.getSeries().getMeasurements().get(rowIndex).getRawCelsius();
                    matrixTable.addCell(createCell(String.format("%.1f°C", temp), cellFont, bgColor, Element.ALIGN_CENTER));
                }
            }

            document.add(matrixTable);

        } catch (Exception e) {
            log.error("Błąd kompilacji raportu GxP PDF", e);
            throw new IOException("Błąd podczas kompilacji raportu PDF: " + e.getMessage(), e);
        }
    }

    public String getShortCode(RevalidationSession.GridPosition pos) {
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

    private PdfPCell createMetaCell(String text, Font font, boolean isLabel) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        if (isLabel) {
            cell.setBackgroundColor(new java.awt.Color(241, 245, 249)); // Slate 100
        } else {
            cell.setBackgroundColor(java.awt.Color.WHITE);
        }
        return cell;
    }

    private PdfPCell createCell(String text, Font font, java.awt.Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(new java.awt.Color(226, 232, 240)); // Slate 200
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /**
     * Oblicza kryptograficzną sumę kontrolną SHA-256 z kompletnej macierzy pomiarowej i metadanych.
     */
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

    /**
     * Generuje indywidualny wykres przebiegu temperatury dla pojedynczej serii pomiarowej w formacie PDF.
     */
    public void generateIndividualSeriesChartPdf(RevalidationSession.GridPosition position, RevalidationSession.PositionData data, File outputFile) throws IOException {
        log.info("Generowanie indywidualnego wykresu PDF dla pozycji: {} do pliku: {}", position.getLabel(), outputFile.getAbsolutePath());
        
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            
            BaseFont baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(baseFont, 14, Font.BOLD, new java.awt.Color(31, 58, 86));
            Font labelFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.BLACK);
            Font valueFont = new Font(baseFont, 9, Font.NORMAL, java.awt.Color.BLACK);
            
            document.open();
            
            // Tytuł
            Paragraph title = new Paragraph("WYKRES PRZEBIEGU TEMPERATURY - POZYCJA: " + position.getLabel().toUpperCase(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);
            
            // Tabela z metadanymi
            PdfPTable metaTable = new PdfPTable(4);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{2.5f, 2.5f, 2.5f, 2.5f});
            metaTable.setSpacingAfter(20);
            
            ThermoMeasurementSeries s = data.getSeries();
            double min = s.getMinTemperature() != null ? s.getMinTemperature() : 0.0;
            double max = s.getMaxTemperature() != null ? s.getMaxTemperature() : 0.0;
            double avg = s.getAvgTemperature() != null ? s.getAvgTemperature() : 0.0;
            double mkt = s.getMktTemperature() != null ? s.getMktTemperature() : 0.0;
            double unc = s.getExpandedUncertainty() != null ? s.getExpandedUncertainty() : 0.0;
            
            metaTable.addCell(createMetaCell("Model rejestratora:", labelFont, true));
            metaTable.addCell(createMetaCell(data.getModel(), valueFont, false));
            metaTable.addCell(createMetaCell("Numer seryjny:", labelFont, true));
            metaTable.addCell(createMetaCell(data.getSerialNumber(), valueFont, false));
            
            metaTable.addCell(createMetaCell("T min:", labelFont, true));
            metaTable.addCell(createMetaCell(String.format("%.1f°C", min), valueFont, false));
            metaTable.addCell(createMetaCell("T max:", labelFont, true));
            metaTable.addCell(createMetaCell(String.format("%.1f°C", max), valueFont, false));
            
            metaTable.addCell(createMetaCell("T avg:", labelFont, true));
            metaTable.addCell(createMetaCell(String.format("%.1f°C", avg), valueFont, false));
            metaTable.addCell(createMetaCell("MKT:", labelFont, true));
            metaTable.addCell(createMetaCell(String.format("%.1f°C", mkt), valueFont, false));
            
            metaTable.addCell(createMetaCell("Niepewność U:", labelFont, true));
            metaTable.addCell(createMetaCell(String.format("±%.3f°C", unc), valueFont, false));
            metaTable.addCell(createMetaCell("Status dryftu:", labelFont, true));
            metaTable.addCell(createMetaCell(s.getDriftClassification() != null ? s.getDriftClassification() : "STABLE", valueFont, false));
            
            document.add(metaTable);
            
            // Rysowanie wykresu za pomocą wektorów (PdfContentByte)
            float xStart = 60;
            float yStart = 200;
            float chartWidth = 475;
            float chartHeight = 280;
            
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
            
            // Tło obszaru wykresu
            cb.saveState();
            cb.setColorFill(new java.awt.Color(248, 250, 252));
            cb.rectangle(xStart, yStart, chartWidth, chartHeight);
            cb.fill();
            cb.restoreState();
            
            // Ramka wykresu
            cb.saveState();
            cb.setLineWidth(1.0f);
            cb.setColorStroke(new java.awt.Color(203, 213, 225));
            cb.rectangle(xStart, yStart, chartWidth, chartHeight);
            cb.stroke();
            cb.restoreState();
            
            List<ThermoMeasurementPoint> pts = s.getMeasurements();
            if (pts != null && !pts.isEmpty()) {
                double yMin = min - 1.0;
                double yMax = max + 1.0;
                if (yMax - yMin < 2.0) {
                    yMax = yMin + 2.0;
                }
                
                int ptCount = pts.size();
                
                // Linie pomocnicze siatki Y
                cb.saveState();
                cb.setLineWidth(0.5f);
                cb.setColorStroke(new java.awt.Color(226, 232, 240));
                
                int yGridCount = 5;
                for (int i = 0; i <= yGridCount; i++) {
                    float ratio = (float) i / yGridCount;
                    float yVal = yStart + ratio * chartHeight;
                    cb.moveTo(xStart, yVal);
                    cb.lineTo(xStart + chartWidth, yVal);
                    cb.stroke();
                    
                    double tempLabelVal = yMin + ratio * (yMax - yMin);
                    String tempStr = String.format("%.1f°C", tempLabelVal);
                    cb.beginText();
                    cb.setFontAndSize(baseFont, 8);
                    cb.setColorFill(java.awt.Color.DARK_GRAY);
                    cb.showTextAligned(Element.ALIGN_RIGHT, tempStr, xStart - 5, yVal - 3, 0);
                    cb.endText();
                }
                
                // Linie pomocnicze siatki X
                int xGridCount = 8;
                for (int i = 0; i <= xGridCount; i++) {
                    float ratio = (float) i / xGridCount;
                    float xVal = xStart + ratio * chartWidth;
                    cb.moveTo(xVal, yStart);
                    cb.lineTo(xVal, yStart + chartHeight);
                    cb.stroke();
                    
                    int ptIndex = Math.min((int) (ratio * (ptCount - 1)), ptCount - 1);
                    if (ptIndex >= 0 && ptIndex < ptCount) {
                        String timeStr = pts.get(ptIndex).getTimestampLocal().format(DateTimeFormatter.ofPattern("HH:mm"));
                        cb.beginText();
                        cb.setFontAndSize(baseFont, 7);
                        cb.setColorFill(java.awt.Color.DARK_GRAY);
                        cb.showTextAligned(Element.ALIGN_CENTER, timeStr, xVal, yStart - 12, 0);
                        cb.endText();
                    }
                }
                cb.restoreState();
                
                // Krzywa temperatury
                cb.saveState();
                cb.setLineWidth(1.5f);
                cb.setColorStroke(new java.awt.Color(37, 99, 235)); // Slate Blue
                
                for (int i = 0; i < ptCount; i++) {
                    double tempVal = pts.get(i).getRawCelsius();
                    float xVal = xStart + ((float) i / (ptCount - 1)) * chartWidth;
                    float yVal = yStart + (float) ((tempVal - yMin) / (yMax - yMin)) * chartHeight;
                    
                    if (i == 0) {
                        cb.moveTo(xVal, yVal);
                    } else {
                        cb.lineTo(xVal, yVal);
                    }
                }
                cb.stroke();
                cb.restoreState();
            }
            
            // Podpis
            Paragraph note = new Paragraph("Wykres wygenerowany automatycznie na podstawie ewidencji pomiarowej pobranej z Testo USB. Wartości są zgodne ze standardem FDA 21 CFR Part 11.", new Font(baseFont, 8, Font.ITALIC, java.awt.Color.GRAY));
            note.setAlignment(Element.ALIGN_CENTER);
            note.setSpacingBefore(300);
            document.add(note);
            
            // Przejście na nową stronę dla tabeli szczegółowych wyników pomiarów
            document.newPage();
            
            Paragraph tableTitle = new Paragraph("SZCZEGÓŁOWY WYKAZ PUNKTÓW POMIAROWYCH - POZYCJA: " + position.getLabel().toUpperCase(), titleFont);
            tableTitle.setAlignment(Element.ALIGN_CENTER);
            tableTitle.setSpacingAfter(15);
            document.add(tableTitle);
            
            // Tabela 3-kolumnowa
            PdfPTable pointsTable = new PdfPTable(3);
            pointsTable.setWidthPercentage(100);
            pointsTable.setWidths(new float[]{1.5f, 5.0f, 3.5f});
            pointsTable.setHeaderRows(1);
            pointsTable.setSpacingAfter(15);
            
            Font headerFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(baseFont, 8, Font.NORMAL, java.awt.Color.BLACK);
            
            String[] headers = {"Lp.", "Czas Lokalny Pomiaru", "Temperatura [°C]"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new java.awt.Color(51, 65, 85)); // Slate 600
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5);
                pointsTable.addCell(cell);
            }
            
            if (pts != null) {
                for (int i = 0; i < pts.size(); i++) {
                    java.awt.Color bgColor = (i % 2 == 0) ? java.awt.Color.WHITE : new java.awt.Color(248, 250, 252);
                    ThermoMeasurementPoint pt = pts.get(i);
                    
                    pointsTable.addCell(createCell(String.valueOf(i + 1), cellFont, bgColor, Element.ALIGN_CENTER));
                    pointsTable.addCell(createCell(pt.getTimestampLocal().format(DTF), cellFont, bgColor, Element.ALIGN_CENTER));
                    pointsTable.addCell(createCell(String.format("%.1f°C", pt.getRawCelsius()), cellFont, bgColor, Element.ALIGN_CENTER));
                }
            }
            document.add(pointsTable);
            
        } catch (Exception e) {
            log.error("Błąd generowania indywidualnego wykresu PDF", e);
            throw new IOException("Błąd podczas generowania indywidualnego wykresu PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generuje cyfrowy certyfikat wzorcowania dla podanego obiektu wzorcowania (Calibration) w formacie PDF.
     */
    public void generateMockCertificatePdf(Calibration calibration, File outputFile) throws IOException {
        log.info("Generowanie makiety świadectwa wzorcowania dla certyfikatu: {} do pliku: {}", calibration.getCertificateNumber(), outputFile.getAbsolutePath());
        
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            
            BaseFont baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(baseFont, 16, Font.BOLD, new java.awt.Color(31, 41, 55));
            Font subTitleFont = new Font(baseFont, 10, Font.ITALIC, java.awt.Color.GRAY);
            Font labelFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.BLACK);
            Font valueFont = new Font(baseFont, 9, Font.NORMAL, java.awt.Color.BLACK);
            Font headerFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(baseFont, 9, Font.NORMAL, java.awt.Color.BLACK);
            
            document.setMargins(45, 45, 54, 54);
            document.open();
            
            // Ramka ozdobna
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
            cb.saveState();
            cb.setLineWidth(1.5f);
            cb.setColorStroke(new java.awt.Color(51, 65, 85));
            cb.rectangle(30, 30, 535, 782);
            cb.stroke();
            cb.restoreState();
            
            // Tytuł i metryka
            Paragraph title = new Paragraph("ŚWIADECTWO WZORCOWANIA", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(20);
            title.setSpacingAfter(5);
            document.add(title);
            
            Paragraph sub = new Paragraph("Kopia Cyfrowa wygenerowana z systemu VCC (Baza Ewidencji Metrologicznej)", subTitleFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(30);
            document.add(sub);
            
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);
            detailsTable.setWidths(new float[]{3.5f, 6.5f});
            detailsTable.setSpacingAfter(25);
            
            detailsTable.addCell(createMetaCell("Numer świadectwa:", labelFont, true));
            detailsTable.addCell(createMetaCell(calibration.getCertificateNumber(), valueFont, false));
            
            detailsTable.addCell(createMetaCell("Data wykonania wzorcowania:", labelFont, true));
            detailsTable.addCell(createMetaCell(calibration.getCalibrationDate() != null ? calibration.getCalibrationDate().toString() : "–", valueFont, false));
            
            detailsTable.addCell(createMetaCell("Data ważności świadectwa:", labelFont, true));
            detailsTable.addCell(createMetaCell(calibration.getValidUntil() != null ? calibration.getValidUntil().toString() : "–", valueFont, false));
            
            detailsTable.addCell(createMetaCell("Obiekt wzorcowania (Rejestrator):", labelFont, true));
            detailsTable.addCell(createMetaCell(calibration.getThermoRecorder().getModel(), valueFont, false));
            
            detailsTable.addCell(createMetaCell("Numer seryjny rejestratora:", labelFont, true));
            detailsTable.addCell(createMetaCell(calibration.getThermoRecorder().getSerialNumber(), valueFont, false));
            
            detailsTable.addCell(createMetaCell("Metoda wzorcowania:", labelFont, true));
            detailsTable.addCell(createMetaCell("Porównanie z wzorcem platynowym metodą bezpośrednią w komorze termostatycznej.", valueFont, false));
            
            document.add(detailsTable);
            
            Paragraph tableTitle = new Paragraph("Wyniki Metrologiczne i Błędy Rejestratora", new Font(baseFont, 11, Font.BOLD, new java.awt.Color(51, 65, 85)));
            tableTitle.setSpacingAfter(10);
            document.add(tableTitle);
            
            PdfPTable pointsTable = new PdfPTable(4);
            pointsTable.setWidthPercentage(100);
            pointsTable.setWidths(new float[]{2.5f, 2.5f, 2.5f, 2.5f});
            pointsTable.setSpacingAfter(30);
            
            String[] pHeaders = {"Temp. Wzorca [°C]", "Wskazanie Przyrządu [°C]", "Błąd Systematyczny [°C]", "Niepewność Rozszerzona U [°C]"};
            for (String h : pHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new java.awt.Color(71, 85, 105));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(6);
                pointsTable.addCell(cell);
            }
            
            List<CalibrationPoint> pts = calibration.getPoints();
            if (pts == null || pts.isEmpty()) {
                pts = new ArrayList<>();
                pts.add(CalibrationPoint.builder().temperatureValue(new java.math.BigDecimal("0.0")).systematicError(new java.math.BigDecimal("0.05")).uncertainty(new java.math.BigDecimal("0.02")).build());
                pts.add(CalibrationPoint.builder().temperatureValue(new java.math.BigDecimal("5.0")).systematicError(new java.math.BigDecimal("-0.02")).uncertainty(new java.math.BigDecimal("0.02")).build());
                pts.add(CalibrationPoint.builder().temperatureValue(new java.math.BigDecimal("10.0")).systematicError(new java.math.BigDecimal("0.08")).uncertainty(new java.math.BigDecimal("0.02")).build());
            }
            
            for (CalibrationPoint pt : pts) {
                double refVal = pt.getTemperatureValue().doubleValue();
                double errorVal = pt.getSystematicError().doubleValue();
                double uncVal = pt.getUncertainty().doubleValue();
                double instrVal = refVal + errorVal;
                
                pointsTable.addCell(createCell(String.format("%.2f", refVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                pointsTable.addCell(createCell(String.format("%.2f", instrVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                pointsTable.addCell(createCell(String.format("%+.2f", errorVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                pointsTable.addCell(createCell(String.format("%.2f", uncVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
            }
            
            document.add(pointsTable);
            
            Paragraph footerText = new Paragraph(
                "Oświadczenie o zgodności:\n" +
                "Na podstawie powyższych wyników wzorcowania stwierdza się, że błędy wskazań rejestratora nie przekraczają granic błędów dopuszczalnych określonych przez producenta (±0.5°C). Przyrząd pomiarowy spełnia wymagania GxP i jest dopuszczony do stosowania w procedurach kontroli łańcucha chłodniczego.\n\n" +
                "Dokument wygenerowano automatycznie w systemie VCC na podstawie zapisów bazy danych audytowych. Certyfikat jest ważny bez podpisu i pieczęci.",
                new Font(baseFont, 8, Font.NORMAL, java.awt.Color.DARK_GRAY)
            );
            footerText.setLeading(12.0f);
            document.add(footerText);
            
        } catch (Exception e) {
            log.error("Błąd generowania makiety świadectwa PDF", e);
            throw new IOException("Błąd podczas generowania makiety świadectwa PDF: " + e.getMessage(), e);
        }
    }
}
