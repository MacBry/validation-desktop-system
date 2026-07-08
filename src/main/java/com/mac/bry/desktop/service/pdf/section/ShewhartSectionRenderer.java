package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.ControlChartCalculator;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import com.mac.bry.desktop.service.stats.NelsonRulesDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ShewhartSectionRenderer implements PdfSectionRenderer {

    @Override
    public void renderSection(Document document, RevalidationSession session, List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService, ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        Paragraph section4_3 = new Paragraph("4.3. Weryfikacja Stabilności Procesu (Karty I-MR i Reguły Nelsona)", PdfStyleHelper.getSectionFont());
        section4_3.setSpacingAfter(8);
        document.add(section4_3);

        PdfPTable shewhartTable = new PdfPTable(7);
        shewhartTable.setWidthPercentage(100);
        shewhartTable.setWidths(new float[]{2.0f, 1.3f, 1.8f, 1.0f, 1.3f, 2.6f, 2.0f});
        shewhartTable.setSpacingAfter(15);

        // Nagłówki
        String[] shewhartHeaders = {
            "Pozycja", "I CL", "I LCL/UCL", "MR CL", "MR UCL", "Naruszenia Nelsona (Karta I)", "Naruszenia MR"
        };
        for (String header : shewhartHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(header, PdfStyleHelper.getHeaderFont()));
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
                    codes.add("Reguła " + v.getRuleNumber() + " (Punkt " + v.getSubgroupIndex() + ")");
                }
                xbarViolationsStr = String.join("\n", codes);
            }

            String sViolationsStr = "Brak naruszeń";
            if (!sViolations.isEmpty()) {
                List<String> codes = new ArrayList<>();
                for (NelsonRulesDetector.Violation v : sViolations) {
                    codes.add("UCL/LCL (Para " + (v.getSubgroupIndex() - 1) + "-" + v.getSubgroupIndex() + ")");
                }
                sViolationsStr = String.join("\n", codes);
            }

            shewhartTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_LEFT));
            shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.2f°C", spcData.getICentralLine()), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.2f / %.2f", spcData.getILcl(), spcData.getIUcl()), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.3f°C", spcData.getMrCentralLine()), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            shewhartTable.addCell(PdfStyleHelper.createCell(String.format("%.3f°C", spcData.getMrUcl()), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));

            java.awt.Color xbarBg = xbarViolations.isEmpty() ? java.awt.Color.WHITE : new java.awt.Color(254, 242, 242);
            java.awt.Color sBg = sViolations.isEmpty() ? java.awt.Color.WHITE : new java.awt.Color(254, 242, 242);

            shewhartTable.addCell(PdfStyleHelper.createCell(xbarViolationsStr, PdfStyleHelper.getCellFont(), xbarBg, Element.ALIGN_LEFT));
            shewhartTable.addCell(PdfStyleHelper.createCell(sViolationsStr, PdfStyleHelper.getCellFont(), sBg, Element.ALIGN_LEFT));
        }
        document.add(shewhartTable);

        // Nowa strona na tabelę pomiarową
        document.newPage();
    }
}
