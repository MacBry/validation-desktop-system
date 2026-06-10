package com.mac.bry.desktop.service.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mac.bry.desktop.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class IndividualChartPdfRenderer {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void render(RevalidationSession.GridPosition position, RevalidationSession.PositionData data, File outputFile) throws IOException {
        log.info("Generowanie indywidualnego wykresu PDF dla pozycji: {} do pliku: {}", position.getLabel(), outputFile.getAbsolutePath());
        
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            
            BaseFont baseFont = PdfStyleHelper.getBaseFont();
            Font titleFont = PdfStyleHelper.getTitleFont();
            Font labelFont = PdfStyleHelper.getLabelFont();
            Font valueFont = PdfStyleHelper.getValueFont();
            
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
            
            metaTable.addCell(PdfStyleHelper.createMetaCell("Model rejestratora:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(data.getModel(), valueFont, false));
            metaTable.addCell(PdfStyleHelper.createMetaCell("Numer seryjny:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(data.getSerialNumber(), valueFont, false));
            
            metaTable.addCell(PdfStyleHelper.createMetaCell("T min:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(String.format("%.1f°C", min), valueFont, false));
            metaTable.addCell(PdfStyleHelper.createMetaCell("T max:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(String.format("%.1f°C", max), valueFont, false));
            
            metaTable.addCell(PdfStyleHelper.createMetaCell("T avg:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(String.format("%.1f°C", avg), valueFont, false));
            metaTable.addCell(PdfStyleHelper.createMetaCell("MKT:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(String.format("%.1f°C", mkt), valueFont, false));
            
            metaTable.addCell(PdfStyleHelper.createMetaCell("Niepewność U:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(String.format("±%.3f°C", unc), valueFont, false));
            metaTable.addCell(PdfStyleHelper.createMetaCell("Status dryftu:", labelFont, true));
            metaTable.addCell(PdfStyleHelper.createMetaCell(s.getDriftClassification() != null ? s.getDriftClassification() : "STABLE", valueFont, false));
            
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
                    
                    pointsTable.addCell(PdfStyleHelper.createCell(String.valueOf(i + 1), cellFont, bgColor, Element.ALIGN_CENTER));
                    pointsTable.addCell(PdfStyleHelper.createCell(pt.getTimestampLocal().format(DTF), cellFont, bgColor, Element.ALIGN_CENTER));
                    pointsTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", pt.getRawCelsius()), cellFont, bgColor, Element.ALIGN_CENTER));
                }
            }
            document.add(pointsTable);
            
        } catch (Exception e) {
            log.error("Błąd generowania indywidualnego wykresu PDF", e);
            throw new IOException("Błąd podczas generowania indywidualnego wykresu PDF: " + e.getMessage(), e);
        }
    }
}
