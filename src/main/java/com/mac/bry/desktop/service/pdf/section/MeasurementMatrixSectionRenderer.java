package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MeasurementMatrixSectionRenderer implements PdfSectionRenderer {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void renderSection(Document document, RevalidationSession session, List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService, ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        // 5. SZCZEGÓŁOWA TABELA ZSYNCHRONIZOWANYCH WYNIKÓW POMIARÓW
        Paragraph section5 = new Paragraph("5. Szczegółowy Wykaz Zsynchronizowanych Serii Pomiarowych", PdfStyleHelper.getSectionFont());
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
        PdfPCell cellLp = new PdfPCell(new Phrase("Lp.", PdfStyleHelper.getHeaderFont()));
        cellLp.setBackgroundColor(new java.awt.Color(30, 41, 59)); // Slate 800
        cellLp.setHorizontalAlignment(Element.ALIGN_CENTER);
        cellLp.setPadding(4);
        matrixTable.addCell(cellLp);

        PdfPCell cellTime = new PdfPCell(new Phrase("Czas Lokalny Pomiaru", PdfStyleHelper.getHeaderFont()));
        cellTime.setBackgroundColor(new java.awt.Color(30, 41, 59));
        cellTime.setHorizontalAlignment(Element.ALIGN_CENTER);
        cellTime.setPadding(4);
        matrixTable.addCell(cellTime);

        // Nagłówki kanałów siatki
        for (RevalidationSession.GridPosition pos : activePositions) {
            String shortCode = getShortCode(pos);
            PdfPCell cellChan = new PdfPCell(new Phrase(shortCode, PdfStyleHelper.getHeaderFont()));
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
            matrixTable.addCell(PdfStyleHelper.createCell(String.valueOf(rowIndex + 1), PdfStyleHelper.getCellFont(), bgColor, Element.ALIGN_CENTER));

            // 2. Czas (pobrany z pierwszego kanału)
            LocalDateTime timestamp = session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().get(rowIndex).getTimestampLocal();
            matrixTable.addCell(PdfStyleHelper.createCell(timestamp.format(DTF), PdfStyleHelper.getCellFont(), bgColor, Element.ALIGN_CENTER));

            // 3. Wartości temperatur dla poszczególnych aktywnych pozycji
            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                double temp = d.getSeries().getMeasurements().get(rowIndex).getRawCelsius();
                matrixTable.addCell(PdfStyleHelper.createCell(String.format("%.1f°C", temp), PdfStyleHelper.getCellFont(), bgColor, Element.ALIGN_CENTER));
            }
        }

        document.add(matrixTable);
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
}
