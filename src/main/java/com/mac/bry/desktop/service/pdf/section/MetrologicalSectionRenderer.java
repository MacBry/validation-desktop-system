package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.dto.stats.CorrectedStatsDTO;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;

import java.awt.Color;
import java.io.File;
import java.util.List;

/**
 * Sekcja 4 raportu PDF: Charakterystyka Metrologiczna oraz Budżet Niepewności.
 *
 * <p>Każda pozycja pomiarowa jest reprezentowana przez <b>dwa wiersze</b>:
 * <ol>
 *   <li>Wiersz surowy (białe tło) — statystyki na {@code rawCelsius}</li>
 *   <li>Wiersz skorygowany (szare tło, oznaczony <b>[Skor.*]</b>) — statystyki
 *       po korekcji błędu systematycznego wzorcowania (interpolacja liniowa GUM §4.3)</li>
 * </ol>
 *
 * <p>Dane skorygowane są odczytywane z {@code session.getCorrectedStatsMap()},
 * które jest wypełniane przez {@code RevalidationReportPdfRenderer} przed wywołaniem renderera.</p>
 */
public class MetrologicalSectionRenderer implements PdfSectionRenderer {

    /** Tło wiersza skorygowanego — jasny slate */
    private static final Color CORRECTED_ROW_BG = new Color(241, 245, 249);
    /** Kolor nagłówka wiersza skorygowanego */
    private static final Color CORRECTED_LABEL_BG = new Color(226, 232, 240);

    @Override
    public void renderSection(Document document, RevalidationSession session,
                              List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService,
                              ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        Paragraph section4 = new Paragraph(
                "4. Charakterystyka Metrologiczna oraz Budżet Niepewności (GUM & GxP)",
                PdfStyleHelper.getSectionFont());
        section4.setSpacingAfter(8);
        document.add(section4);

        PdfPTable metrologicalTable = new PdfPTable(9);
        metrologicalTable.setWidthPercentage(100);
        metrologicalTable.setWidths(new float[]{2.0f, 1.8f, 1.2f, 1.2f, 1.2f, 1.2f, 1.5f, 1.0f, 1.8f});
        metrologicalTable.setSpacingAfter(8);

        // --- Nagłówki ---
        String[] metroHeaders = {"Pozycja", "S/N", "T min", "T max", "T avg", "MKT", "Niepewność U", "Szpilki", "Dryft"};
        for (String header : metroHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(header, PdfStyleHelper.getHeaderFont()));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            cell.setBackgroundColor(new Color(51, 65, 85));
            metrologicalTable.addCell(cell);
        }

        // --- Wiersze danych ---
        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            ThermoMeasurementSeries s = d.getSeries();

            double min  = s.getMinTemperature()    != null ? s.getMinTemperature()    : 0.0;
            double max  = s.getMaxTemperature()    != null ? s.getMaxTemperature()    : 0.0;
            double avg  = s.getAvgTemperature()    != null ? s.getAvgTemperature()    : 0.0;
            double mkt  = s.getMktTemperature()    != null ? s.getMktTemperature()    : 0.0;
            double unc  = s.getExpandedUncertainty() != null ? s.getExpandedUncertainty() : 0.0;
            int spikes  = s.getSpikeCount()        != null ? s.getSpikeCount()        : 0;
            String drift = s.getDriftClassification() != null ? s.getDriftClassification() : "STABLE";

            // --- Wiersz 1: Surowy ---
            metrologicalTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_LEFT));
            metrologicalTable.addCell(PdfStyleHelper.createCell(d.getSerialNumber(), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", min), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", max), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", avg), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", mkt), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.format("±%.3f°C", unc), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            metrologicalTable.addCell(PdfStyleHelper.createCell(String.valueOf(spikes), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));

            Color driftBg = getDriftColor(drift);
            metrologicalTable.addCell(PdfStyleHelper.createCell(drift, PdfStyleHelper.getCellFont(), driftBg, Element.ALIGN_CENTER));

            // --- Wiersz 2: Skorygowany* ---
            CorrectedStatsDTO corrected = session.getCorrectedStatsMap().get(pos);
            addCorrectedMetrologicalRow(metrologicalTable, corrected);
        }

        document.add(metrologicalTable);

        // Legenda
        Paragraph legend = new Paragraph(
                "* Wartości oznaczone [Skor.*] zostały skorygowane o błąd systematyczny wzorcowania " +
                "metodą interpolacji liniowej między punktami CalibrationPoint (GUM §4.3). " +
                "MKT, Szpilki i Dryft nie są wyznaczane dla wartości skorygowanych.",
                PdfStyleHelper.getFooterFont());
        legend.setSpacingAfter(15);
        document.add(legend);

        document.newPage();
    }

    /**
     * Dodaje wiersz skorygowany do tabeli metrologicznej.
     * Jeśli brak danych wzorcowania — wyświetla komunikat spanning całą szerokość.
     */
    private void addCorrectedMetrologicalRow(PdfPTable table, CorrectedStatsDTO dto) {
        if (dto == null || !dto.isHasCalibrationData()) {
            // Scalona komórka z komunikatem o braku danych
            PdfPCell noDataCell = new PdfPCell(new Phrase(
                    "[Skor.*]  Brak danych wzorcowania — korekta niemożliwa",
                    PdfStyleHelper.getCellFont()));
            noDataCell.setColspan(9);
            noDataCell.setBackgroundColor(CORRECTED_ROW_BG);
            noDataCell.setPadding(4);
            noDataCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(noDataCell);
            return;
        }

        table.addCell(PdfStyleHelper.createCell("[Skor.*]", PdfStyleHelper.getCellFont(), CORRECTED_LABEL_BG, Element.ALIGN_LEFT));
        table.addCell(PdfStyleHelper.createCell("—", PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER));
        table.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", dto.getMinCorrected()), PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER));
        table.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", dto.getMaxCorrected()), PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER));
        table.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", dto.getAvgCorrected()), PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER));
        table.addCell(PdfStyleHelper.createCell("—", PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER)); // MKT nie dotyczy
        table.addCell(PdfStyleHelper.createCell(String.format("±%.3f°C", dto.getExpandedUncertaintyCorrected()), PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER));
        table.addCell(PdfStyleHelper.createCell("—", PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER)); // Szpilki nie dotyczą
        table.addCell(PdfStyleHelper.createCell("—", PdfStyleHelper.getCellFont(), CORRECTED_ROW_BG, Element.ALIGN_CENTER)); // Dryft nie dotyczy
    }

    private Color getDriftColor(String drift) {
        return switch (drift) {
            case "STABLE" -> new Color(240, 253, 244);
            case "SPIKE"  -> new Color(239, 246, 255);
            case "DRIFT"  -> new Color(254, 242, 242);
            default       -> new Color(255, 251, 235); // MIXED
        };
    }
}
