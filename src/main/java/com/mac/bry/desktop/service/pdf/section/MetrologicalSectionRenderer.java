package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;

import java.io.File;
import java.util.List;

public class MetrologicalSectionRenderer implements PdfSectionRenderer {

    @Override
    public void renderSection(Document document, RevalidationSession session, List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService, ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        // 5. CHARAKTERYSTYKA METROLOGICZNA I BUDŻET NIEPEWNOŚCI
        Paragraph section4 = new Paragraph("4. Charakterystyka Metrologiczna oraz Budżet Niepewności (GUM & GxP)", PdfStyleHelper.getSectionFont());
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
            PdfPCell cell = new PdfPCell(new Phrase(header, PdfStyleHelper.getHeaderFont()));
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

            metrologicalTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_LEFT));
            metrologicalTable.addCell(PdfStyleHelper.createCell(d.getSerialNumber(), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", min), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", max), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", avg), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", mkt), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("±%.3f°C", unc), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.valueOf(spikes), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));

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
            metrologicalTable.addCell(PdfStyleHelper.createCell(drift, PdfStyleHelper.getCellFont(), driftBg, Element.ALIGN_CENTER));
        }
        document.add(metrologicalTable);

        // Nowa strona na analizę statystyczną i wnioski GxP
        document.newPage();
    }
}
