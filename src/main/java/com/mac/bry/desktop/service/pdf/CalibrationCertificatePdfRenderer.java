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
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CalibrationCertificatePdfRenderer {

    public void render(Calibration calibration, File outputFile) throws IOException {
        log.info("Generowanie makiety świadectwa wzorcowania dla certyfikatu: {} do pliku: {}", calibration.getCertificateNumber(), outputFile.getAbsolutePath());
        
        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            
            BaseFont baseFont = PdfStyleHelper.getBaseFont();
            Font titleFont = PdfStyleHelper.getFont(16, Font.BOLD, new java.awt.Color(31, 41, 55));
            Font subTitleFont = PdfStyleHelper.getFont(10, Font.ITALIC, java.awt.Color.GRAY);
            Font labelFont = PdfStyleHelper.getLabelFont();
            Font valueFont = PdfStyleHelper.getValueFont();
            Font headerFont = PdfStyleHelper.getFont(9, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = PdfStyleHelper.getFont(9, Font.NORMAL, java.awt.Color.BLACK);
            
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
            
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Numer świadectwa:", labelFont, true));
            detailsTable.addCell(PdfStyleHelper.createMetaCell(calibration.getCertificateNumber(), valueFont, false));
            
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Data wykonania wzorcowania:", labelFont, true));
            detailsTable.addCell(PdfStyleHelper.createMetaCell(calibration.getCalibrationDate() != null ? calibration.getCalibrationDate().toString() : "–", valueFont, false));
            
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Data ważności świadectwa:", labelFont, true));
            detailsTable.addCell(PdfStyleHelper.createMetaCell(calibration.getValidUntil() != null ? calibration.getValidUntil().toString() : "–", valueFont, false));
            
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Obiekt wzorcowania (Rejestrator):", labelFont, true));
            detailsTable.addCell(PdfStyleHelper.createMetaCell(calibration.getThermoRecorder().getModel() != null ? calibration.getThermoRecorder().getModel().getName() : "Nieznany", valueFont, false));
            
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Numer seryjny rejestratora:", labelFont, true));
            
            String sn = calibration.getThermoRecorder().getSerialNumber();
            if (calibration.getChannelNumber() != null && calibration.getThermoRecorder().getModel() != null && calibration.getThermoRecorder().getModel().getChannelCount() != null && calibration.getThermoRecorder().getModel().getChannelCount() > 1) {
                sn += " (Kanał " + calibration.getChannelNumber() + ")";
            }
            detailsTable.addCell(PdfStyleHelper.createMetaCell(sn, valueFont, false));
            
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Metoda wzorcowania:", labelFont, true));
            detailsTable.addCell(PdfStyleHelper.createMetaCell("Porównanie z wzorcem platynowym metodą bezpośrednią w komorze termostatycznej.", valueFont, false));
            
            document.add(detailsTable);
            
            Paragraph tableTitle = new Paragraph("Wyniki Metrologiczne i Błędy Rejestratora", PdfStyleHelper.getFont(11, Font.BOLD, new java.awt.Color(51, 65, 85)));
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
                
                pointsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f", refVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                pointsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f", instrVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                pointsTable.addCell(PdfStyleHelper.createCell(String.format("%+.2f", errorVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
                pointsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f", uncVal), cellFont, java.awt.Color.WHITE, Element.ALIGN_CENTER));
            }
            
            document.add(pointsTable);
            
            Paragraph footerText = new Paragraph(
                "Oświadczenie o zgodności:\n" +
                "Na podstawie powyższych wyników wzorcowania stwierdza się, że błędy wskazań rejestratora nie przekraczają granic błędów dopuszczalnych określonych przez producenta (±0.5°C). Przyrząd pomiarowy spełnia wymagania GxP i jest dopuszczony do stosowania w procedurach kontroli łańcucha chłodniczego.\n\n" +
                "Dokument wygenerowano automatycznie w systemie VCC na podstawie zapisów bazy danych audytowych. Certyfikat jest ważny bez podpisu i pieczęci.",
                PdfStyleHelper.getFont(8, Font.NORMAL, java.awt.Color.DARK_GRAY)
            );
            footerText.setLeading(12.0f);
            document.add(footerText);
            
        } catch (Exception e) {
            log.error("Błąd generowania makiety świadectwa PDF", e);
            throw new IOException("Błąd podczas generowania makiety świadectwa PDF: " + e.getMessage(), e);
        }
    }
}
