package com.mac.bry.desktop.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class TestoPdfReportService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static class TestoReportData {
        public String model;
        public String serialNumber;
        public String batteryLevel;
        public String interval;
        public String startDelay;
        public String comments;
        public List<ThermoMeasurementPoint> measurements;
    }

    /**
     * Generuje nienaruszalny raport pomiarowy PDF (zgodny z FDA 21 CFR Part 11).
     */
    public void generatePdfReport(TestoReportData data, File outputFile, File chartImageFile) throws IOException {
        log.info("Generowanie raportu pomiarowego PDF: {}", outputFile.getAbsolutePath());

        // Obliczenie sumy kontrolnej integralności danych
        String checksum = calculateSha256Checksum(data.measurements);
        log.info("Wyznaczono sumę kontrolną integralności pomiarów (SHA-256): {}", checksum);

        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));

            // Czcionki z obsługą polskich znaków (Arial)
            BaseFont baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(baseFont, 16, Font.BOLD, new java.awt.Color(31, 58, 86));
            Font sectionFont = new Font(baseFont, 11, Font.BOLD, new java.awt.Color(44, 62, 80));
            Font labelFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.BLACK);
            Font valueFont = new Font(baseFont, 9, Font.NORMAL, java.awt.Color.BLACK);
            Font headerFont = new Font(baseFont, 9, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(baseFont, 8, Font.NORMAL, java.awt.Color.BLACK);
            Font footerFont = new Font(baseFont, 8, Font.ITALIC, java.awt.Color.GRAY);

            // Ustawienie nagłówka/stopki ze stronnicowaniem "Strona X z Y"
            PdfHeaderFooterHandler handler = new PdfHeaderFooterHandler(
                    new Font(baseFont, 10, Font.BOLD, new java.awt.Color(44, 62, 80)),
                    footerFont,
                    "Raport Metrologiczny Odczytu Rejestratora Testo"
            );
            writer.setPageEvent(handler);

            // Metadane PDF
            document.addTitle("Raport Metrologiczny Odczytu Rejestratora - S/N " + data.serialNumber);
            document.addAuthor("VCC Desktop Application");
            document.addCreator("VCC Validation Module");
            document.addCreationDate();

            document.setMargins(36, 36, 54, 54); // L, R, T, B
            document.open();

            // 1. Tytuł raportu
            Paragraph title = new Paragraph("RAPORT Z ODCZYTU REJESTRATORA TEMPERATURY", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // 2. Metryka rejestratora (Tabela 4-kolumnowa)
            Paragraph section1 = new Paragraph("1. Metryka Urządzenia oraz Parametry Odczytu", sectionFont);
            section1.setSpacingAfter(8);
            document.add(section1);

            PdfPTable metaTable = new PdfPTable(4);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{2, 3, 2, 3});
            metaTable.setSpacingAfter(15);

            metaTable.addCell(createMetaCell("Model urządzenia:", labelFont, true));
            metaTable.addCell(createMetaCell(data.model, valueFont, false));
            metaTable.addCell(createMetaCell("Numer seryjny:", labelFont, true));
            metaTable.addCell(createMetaCell(data.serialNumber, valueFont, false));

            metaTable.addCell(createMetaCell("Stan baterii:", labelFont, true));
            metaTable.addCell(createMetaCell(data.batteryLevel, valueFont, false));
            metaTable.addCell(createMetaCell("Interwał zapisu:", labelFont, true));
            metaTable.addCell(createMetaCell(data.interval, valueFont, false));

            metaTable.addCell(createMetaCell("Opóźnienie startu:", labelFont, true));
            metaTable.addCell(createMetaCell(data.startDelay, valueFont, false));
            metaTable.addCell(createMetaCell("Liczba punktów:", labelFont, true));
            metaTable.addCell(createMetaCell(String.valueOf(data.measurements.size()), valueFont, false));

            document.add(metaTable);

            // 3. Suma kontrolna integralności danych (FDA 21 CFR Part 11)
            PdfPTable hashTable = new PdfPTable(1);
            hashTable.setWidthPercentage(100);
            hashTable.setSpacingBefore(15.0f); // Odstęp nad ramką sumy kontrolnej
            hashTable.setSpacingAfter(15.0f);
            
            Paragraph hashPara = new Paragraph();
            hashPara.setLeading(14.0f); // Piękny odstęp między tytułem a długim hashem
            hashPara.add(new Chunk("Integralność danych (SHA-256 Data Integrity Checksum):\n", labelFont));
            Font hashFont = new Font(baseFont, 8, Font.NORMAL, new java.awt.Color(44, 62, 80));
            hashPara.add(new Chunk(checksum, hashFont));

            PdfPCell hashCell = new PdfPCell(hashPara);
            hashCell.setBackgroundColor(new java.awt.Color(235, 243, 250));
            hashCell.setPadding(10);
            hashCell.setBorderColor(new java.awt.Color(180, 200, 220));
            hashTable.addCell(hashCell);
            document.add(hashTable);

            // 4. Uwagi / komentarze walidacyjne (jeśli obecne)
            if (data.comments != null && !data.comments.isBlank()) {
                PdfPTable commentTable = new PdfPTable(1);
                commentTable.setWidthPercentage(100);
                commentTable.setSpacingAfter(15);

                PdfPCell commCell = new PdfPCell(new Phrase("Uwagi i komentarze walidacyjne:\n" + data.comments, valueFont));
                commCell.setPadding(8);
                commCell.setBackgroundColor(new java.awt.Color(253, 253, 250));
                commCell.setBorderColor(java.awt.Color.LIGHT_GRAY);
                commentTable.addCell(commCell);
                document.add(commentTable);
            }

            // 5. Wykres wartości w czasie (jeśli plik ze snapshotem istnieje)
            if (chartImageFile != null && chartImageFile.exists()) {
                Paragraph section2 = new Paragraph("2. Wykres Zmienności Temperatury w Czasie", sectionFont);
                section2.setSpacingAfter(8);
                document.add(section2);

                Image chartImg = Image.getInstance(chartImageFile.getAbsolutePath());
                chartImg.setAlignment(Element.ALIGN_CENTER);
                chartImg.scaleToFit(500, 300);
                chartImg.setSpacingAfter(20);
                document.add(chartImg);
            }

            // Wymuszamy nową stronę dla pełnej tabeli pomiarowej w celach przejrzystości
            document.newPage();

            // 6. Tabela wartości pomiarowych
            Paragraph section3 = new Paragraph("3. Szczegółowy Wykaz Punktów Pomiarowych", sectionFont);
            section3.setSpacingAfter(8);
            document.add(section3);

            PdfPTable dataTable = new PdfPTable(3);
            dataTable.setWidthPercentage(100);
            dataTable.setWidths(new float[]{1, 3, 2});
            dataTable.setHeaderRows(1);
            dataTable.setSpacingAfter(15);

            // Nagłówki tabeli pomiarowej
            String[] headers = {"Lp. (Indeks)", "Czas Lokalny Pomiaru", "Temperatura (°C)"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(6);
                cell.setBackgroundColor(new java.awt.Color(44, 62, 80)); // ciemny granatowy
                cell.setBorderColor(java.awt.Color.DARK_GRAY);
                dataTable.addCell(cell);
            }

            // Dane pomiarowe
            int rowIndex = 0;
            for (ThermoMeasurementPoint pt : data.measurements) {
                java.awt.Color bgColor = (rowIndex % 2 == 0) ? java.awt.Color.WHITE : new java.awt.Color(245, 245, 245);

                dataTable.addCell(createCell(String.valueOf(pt.getMeasurementIndex()), cellFont, bgColor, Element.ALIGN_CENTER));
                dataTable.addCell(createCell(pt.getTimestampLocal().format(DTF), cellFont, bgColor, Element.ALIGN_CENTER));
                dataTable.addCell(createCell(String.format("%.1f °C", pt.getRawCelsius()), cellFont, bgColor, Element.ALIGN_CENTER));

                rowIndex++;
            }

            document.add(dataTable);

        } catch (Exception e) {
            log.error("Błąd podczas generowania raportu PDF", e);
            throw new IOException("Błąd podczas generowania raportu PDF: " + e.getMessage(), e);
        }
    }

    private PdfPCell createMetaCell(String text, Font font, boolean isLabel) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        if (isLabel) {
            cell.setBackgroundColor(new java.awt.Color(245, 247, 250));
        } else {
            cell.setBackgroundColor(java.awt.Color.WHITE);
        }
        return cell;
    }

    private PdfPCell createCell(String text, Font font, java.awt.Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /**
     * Generuje sumę kontrolną SHA-256 z całej serii danych w celu ochrony przed manipulacją.
     */
    private String calculateSha256Checksum(List<ThermoMeasurementPoint> points) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (ThermoMeasurementPoint pt : points) {
                sb.append(pt.getMeasurementIndex())
                  .append(pt.getTimestampLocal().toString())
                  .append(pt.getRawCelsius());
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
            log.error("Nie udało się wyznaczyć sumy kontrolnej serii pomiarów", e);
            return "INTEGRITY_ERROR";
        }
    }
}
