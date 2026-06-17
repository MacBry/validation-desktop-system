package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;

import java.io.File;
import java.util.List;

public class TraceabilitySectionRenderer implements PdfSectionRenderer {

    @Override
    public void renderSection(Document document, RevalidationSession session, List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService, ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        // 2. MATRYCA MAPOWANIA REJESTRATORÓW I ICH ŚWIADECTW WZORCOWANIA
        Paragraph section2 = new Paragraph("2. Traceability - Wykaz Rejestratorów oraz Świadectw Wzorcowania", PdfStyleHelper.getSectionFont());
        section2.setSpacingAfter(8);
        document.add(section2);

        PdfPTable traceabilityTable = new PdfPTable(5);
        traceabilityTable.setWidthPercentage(100);
        traceabilityTable.setWidths(new float[]{2.5f, 2, 2, 2.5f, 2});
        traceabilityTable.setSpacingAfter(15);

        // Nagłówki
        String[] traceHeaders = {"Fizyczna Pozycja", "Model", "Numer Seryjny", "Certyfikat Wzorcowania", "Ważność Certyfikatu"};
        for (String header : traceHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(header, PdfStyleHelper.getHeaderFont()));
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

            traceabilityTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_LEFT));
            String modelName = d.getModel() != null ? d.getModel().getName() : "";
            traceabilityTable.addCell(PdfStyleHelper.createCell(modelName, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            
            String snDisplay = d.getSerialNumber();
            if (d.getModel() != null && d.getModel().getChannelCount() != null && d.getModel().getChannelCount() > 1 && d.getChannelNumber() != null) {
                snDisplay += " (Ch " + d.getChannelNumber() + ")";
            }
            traceabilityTable.addCell(PdfStyleHelper.createCell(snDisplay, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            
            traceabilityTable.addCell(PdfStyleHelper.createCell(certNo, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            traceabilityTable.addCell(PdfStyleHelper.createCell(validity, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
        }
        document.add(traceabilityTable);

        // 3. INTEGRALNOŚĆ DANYCH (SHA-256)
        PdfPTable hashTable = new PdfPTable(1);
        hashTable.setWidthPercentage(100);
        hashTable.setSpacingAfter(15);

        Paragraph hashPara = new Paragraph();
        hashPara.setLeading(14.0f);
        hashPara.add(new Chunk("Kryptograficzna Suma Kontrolna Integralności GxP (SHA-256 Data Integrity Checksum):\n", PdfStyleHelper.getLabelFont()));
        Font hashFont = new Font(PdfStyleHelper.getBaseFont(), 7.5f, Font.NORMAL, new java.awt.Color(44, 62, 80));
        hashPara.add(new Chunk(checksum, hashFont));

        PdfPCell hashCell = new PdfPCell(hashPara);
        hashCell.setBackgroundColor(new java.awt.Color(240, 253, 250)); // Light mint emerald
        hashCell.setPadding(10);
        hashCell.setBorderColor(new java.awt.Color(153, 246, 228));
        hashTable.addCell(hashCell);
        document.add(hashTable);

        // 4. MULTI-KANAŁOWY WYKRES ROZKŁADU TEMPERATUR
        if (chartImageFile != null && chartImageFile.exists()) {
            document.newPage();

            Paragraph section3 = new Paragraph("3. Zintegrowany Przebieg Temperatury w Komorze Chłodniczej", PdfStyleHelper.getSectionFont());
            section3.setSpacingAfter(8);
            document.add(section3);

            try {
                Image chartImg = Image.getInstance(chartImageFile.getAbsolutePath());
                chartImg.setAlignment(Element.ALIGN_CENTER);
                chartImg.scaleToFit(500, 240);
                chartImg.setSpacingAfter(15);
                document.add(chartImg);
            } catch (Exception e) {
                // Ignore image load error
            }
        } else {
            document.newPage();
        }
    }
}
