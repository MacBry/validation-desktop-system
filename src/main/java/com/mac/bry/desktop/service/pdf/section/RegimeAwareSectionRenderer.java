package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ConditionalStatsDTO;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.regime.CausalHypothesisGenerator;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import com.mac.bry.desktop.service.stats.SpcEngine;

import java.awt.Color;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 4.0 PDF: Interpretacja Reżimów Pracy (Regime-Aware Interpretation Layer).
 * Prezentuje:
 * 1. Oś czasu z kolorowymi segmentami (wizualizacja dynamiczna).
 * 2. Tabelę z porównaniem statystyk SPC całego przebiegu vs faza ustalona.
 * 3. Log zdarzeń anomalnych (defrosty, otwarcia drzwi, zmiany nastaw).
 */
public class RegimeAwareSectionRenderer implements PdfSectionRenderer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void renderSection(Document document, RevalidationSession session,
                               List<RevalidationSession.GridPosition> activePositions,
                               HypothesisTestingService hypothesisTestingService,
                               ValidationPlanNumberRepository validationPlanNumberRepository,
                               File chartImageFile, String checksum) throws DocumentException {

        // Nagłówek sekcji
        Paragraph sectionHeader = new Paragraph(
                "4.0. Analiza Reżimów Pracy (Regime-Aware Layer)",
                PdfStyleHelper.getSectionFont());
        sectionHeader.setSpacingAfter(8);
        document.add(sectionHeader);

        // Opis
        Paragraph desc = new Paragraph(
                "Zgodnie z zasadami deterministycznej interpretacji czasowo-przyczynowej (DP-001 §4), " +
                "przebieg pomiarowy został automatycznie podzielony na segmenty o charakterze przejściowym " +
                "(stabilizacja parametrów, odszranianie, otwarcia drzwi) oraz stan ustalony. " +
                "Statystyki kwalifikacyjne procesów GxP wyliczane są wyłącznie w oparciu o fazę ustaloną (STEADY_STATE).",
                PdfStyleHelper.getCellFont());
        desc.setSpacingAfter(15);
        document.add(desc);

        // 1. Wizualizacja osi czasu segmentów
        Paragraph subHeaderTimeline = new Paragraph("Wizualizacja osi czasu reżimów pracy per pozycja", PdfStyleHelper.getLabelFont());
        subHeaderTimeline.setSpacingAfter(6);
        document.add(subHeaderTimeline);

        PdfPTable timelineTable = new PdfPTable(2);
        timelineTable.setWidthPercentage(100);
        timelineTable.setWidths(new float[]{1.8f, 8.2f});
        timelineTable.setSpacingAfter(10);

        // Nagłówki osi czasu
        timelineTable.addCell(PdfStyleHelper.createCell("Pozycja (S/N)", PdfStyleHelper.getHeaderFont(), new Color(51, 65, 85), Element.ALIGN_CENTER));
        timelineTable.addCell(PdfStyleHelper.createCell("Przebieg czasowy segmentów reżimu", PdfStyleHelper.getHeaderFont(), new Color(51, 65, 85), Element.ALIGN_CENTER));

        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            String label = String.format("%s\n(%s)", PdfStyleHelper.getShortCode(pos), d.getSerialNumber());
            timelineTable.addCell(PdfStyleHelper.createCell(label, PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));

            List<MeasurementSegment> segments = session.getDetectedSegmentsMap().get(pos);
            PdfPCell timelineCell = new PdfPCell();
            timelineCell.setPadding(3);
            timelineCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            timelineCell.setBorderColor(new Color(226, 232, 240));

            if (segments == null || segments.isEmpty()) {
                timelineCell.addElement(new Paragraph("Brak danych segmentacji", PdfStyleHelper.getFooterFont()));
            } else {
                timelineCell.addElement(createTimelineBar(segments));
            }
            timelineTable.addCell(timelineCell);
        }
        document.add(timelineTable);

        // Legenda kolorów segmentów
        renderTimelineLegend(document);

        // 2. Tabela porównawcza SPC
        Paragraph subHeaderSpc = new Paragraph("Zdolność procesu w stanie ustalonym vs cały przebieg", PdfStyleHelper.getLabelFont());
        subHeaderSpc.setSpacingAfter(6);
        document.add(subHeaderSpc);

        PdfPTable spcTable = new PdfPTable(7);
        spcTable.setWidthPercentage(100);
        spcTable.setWidths(new float[]{1.8f, 1.2f, 1.3f, 1.3f, 1.3f, 1.3f, 1.8f});
        spcTable.setSpacingAfter(15);

        String[] spcHeaders = {
            "Pozycja", "% STEADY", "Cp (Cały)", "Cp (STEADY)", "Cpk (Cały)", "Cpk (STEADY)", "Werdykt SPC"
        };
        for (String header : spcHeaders) {
            spcTable.addCell(PdfStyleHelper.createCell(header, PdfStyleHelper.getHeaderFont(), new Color(51, 65, 85), Element.ALIGN_CENTER));
        }

        CoolingChamber chamber = session.getCoolingChamber();
        Double lsl = (chamber != null) ? chamber.getMinOperatingTemp() : null;
        Double usl = (chamber != null) ? chamber.getMaxOperatingTemp() : null;

        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            ThermoMeasurementSeries s = d.getSeries();
            ConditionalStatsDTO condDto = session.getConditionalStatsMap().get(pos);

            double[] rawValues = s.getMeasurements() != null ? s.getMeasurements().stream()
                    .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                    .toArray() : new double[0];

            Double cpRaw = null;
            Double cpkRaw = null;
            if (lsl != null && usl != null && rawValues.length >= 2) {
                CapabilityIndexes rawCap = SpcEngine.calculateCapability(rawValues, lsl, usl);
                cpRaw = rawCap.getCp();
                cpkRaw = rawCap.getCpk();
            }

            String pctSteadyStr = "0%";
            String cpSteadyStr = "–";
            String cpkSteadyStr = "–";
            String verdictText = "BRAK DANYCH";
            Color verdictBg = new Color(241, 245, 249); // Slate 100

            if (condDto != null) {
                pctSteadyStr = String.format("%.1f%%", condDto.getSteadyStateCoveragePercent());
                if (condDto.isHasSteadyStateData()) {
                    cpSteadyStr = condDto.getCpSteady() != null ? String.format("%.2f", condDto.getCpSteady()) : "–";
                    cpkSteadyStr = condDto.getCpkSteady() != null ? String.format("%.2f", condDto.getCpkSteady()) : "–";

                    if (condDto.getVerdictNote() == null) {
                        verdictText = "PASS";
                        verdictBg = new Color(220, 252, 231); // Green 100
                    } else {
                        String note = condDto.getVerdictNote();
                        if (note.startsWith("FAIL")) {
                            verdictText = "FAIL";
                            verdictBg = new Color(254, 226, 226); // Red 100
                        } else if (note.startsWith("WARNING")) {
                            verdictText = "WARNING";
                            verdictBg = new Color(254, 243, 199); // Amber 100
                        } else if (note.startsWith("FINDING")) {
                            verdictText = "FINDING";
                            verdictBg = new Color(243, 232, 255); // Purple 100
                        } else if (note.startsWith("INCONCLUSIVE")) {
                            verdictText = "INCONCLUSIVE";
                            verdictBg = new Color(254, 243, 199); // Amber 100
                        } else {
                            verdictText = "WARNING";
                            verdictBg = new Color(254, 243, 199);
                        }
                    }
                } else {
                    verdictText = "INCONCLUSIVE";
                    verdictBg = new Color(254, 243, 199); // Amber 100
                }
            }

            spcTable.addCell(PdfStyleHelper.createCell(PdfStyleHelper.getShortCode(pos), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            spcTable.addCell(PdfStyleHelper.createCell(pctSteadyStr, PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            spcTable.addCell(PdfStyleHelper.createCell(cpRaw != null ? String.format("%.2f", cpRaw) : "–", PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            spcTable.addCell(PdfStyleHelper.createCell(cpSteadyStr, PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            spcTable.addCell(PdfStyleHelper.createCell(cpkRaw != null ? String.format("%.2f", cpkRaw) : "–", PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            spcTable.addCell(PdfStyleHelper.createCell(cpkSteadyStr, PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
            spcTable.addCell(PdfStyleHelper.createCell(verdictText, PdfStyleHelper.getLabelFont(), verdictBg, Element.ALIGN_CENTER));
        }
        document.add(spcTable);

        // 3. Log zdarzeń i zmian reżimów (Event Log)
        Paragraph subHeaderEvents = new Paragraph("Dziennik zdarzeń i anomalii dynamicznych (Event Log)", PdfStyleHelper.getLabelFont());
        subHeaderEvents.setSpacingAfter(6);
        document.add(subHeaderEvents);

        List<MeasurementSegment> eventSegments = new ArrayList<>();
        for (RevalidationSession.GridPosition pos : activePositions) {
            List<MeasurementSegment> segments = session.getDetectedSegmentsMap().get(pos);
            if (segments != null) {
                for (MeasurementSegment seg : segments) {
                    if (seg.getType() != SegmentType.STEADY_STATE && seg.getType() != SegmentType.EQUILIBRATION) {
                        eventSegments.add(seg);
                    }
                }
            }
        }

        if (eventSegments.isEmpty()) {
            Paragraph noEvents = new Paragraph(
                    "W analizowanym przebiegu nie wykryto żadnych anomalii okresowych (defrosty), " +
                    "naruszeń (ekskursje) ani zmian nastaw temperatur (CUSUM). Komora pracowała w reżimach standardowych.",
                    PdfStyleHelper.getCellFont());
            noEvents.setSpacingAfter(15);
            document.add(noEvents);
        } else {
            PdfPTable eventTable = new PdfPTable(5);
            eventTable.setWidthPercentage(100);
            eventTable.setWidths(new float[]{1.2f, 1.8f, 2.0f, 2.0f, 3.0f});
            eventTable.setSpacingAfter(15);

            String[] eventHeaders = {"Kanał", "Typ zdarzenia", "Czas rozpoczęcia", "Czas zakończenia", "Komentarz / Sygnatura"};
            for (String header : eventHeaders) {
                eventTable.addCell(PdfStyleHelper.createCell(header, PdfStyleHelper.getHeaderFont(), new Color(51, 65, 85), Element.ALIGN_CENTER));
            }

            for (MeasurementSegment seg : eventSegments) {
                String posLabel = seg.getSeries().getGridPosition() != null ? PdfStyleHelper.getShortCode(seg.getSeries().getGridPosition()) : "–";
                eventTable.addCell(PdfStyleHelper.createCell(posLabel, PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
                eventTable.addCell(PdfStyleHelper.createCell(getEventTypeName(seg.getType()), PdfStyleHelper.getLabelFont(), getSegmentColor(seg.getType()), Element.ALIGN_CENTER));
                eventTable.addCell(PdfStyleHelper.createCell(seg.getFromTimestamp().format(TIME_FORMATTER), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
                eventTable.addCell(PdfStyleHelper.createCell(seg.getToTimestamp().format(TIME_FORMATTER), PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_CENTER));
                eventTable.addCell(PdfStyleHelper.createCell(seg.getNote() != null ? seg.getNote() : "–", PdfStyleHelper.getCellFont(), Color.WHITE, Element.ALIGN_LEFT));
            }
            document.add(eventTable);

            // 4. Hipotezy przyczynowe (DP-001 §4.6, Faza 5) — zdania sterowane
            //    policzonymi cechami sygnału zamiast szablonowego akapitu hipotez
            Paragraph subHeaderHypotheses = new Paragraph(
                    "Hipotezy przyczynowe (interpretacja czasowo-przyczynowa)", PdfStyleHelper.getLabelFont());
            subHeaderHypotheses.setSpacingAfter(6);
            document.add(subHeaderHypotheses);

            List<String> hypotheses = new CausalHypothesisGenerator().generateHypotheses(eventSegments);
            com.lowagie.text.List hypothesisList = new com.lowagie.text.List(false, 12);
            for (String hypothesis : hypotheses) {
                hypothesisList.add(new com.lowagie.text.ListItem(hypothesis, PdfStyleHelper.getCellFont()));
            }
            document.add(hypothesisList);

            Paragraph hypothesesFootnote = new Paragraph(
                    "Hipotezy wygenerowane deterministycznie z cech sygnału (czas, amplituda, czas powrotu, "
                            + "sygnatura przestrzenna). Zdarzenia oznaczone [ODRZUCONE] zostały zweryfikowane "
                            + "negatywnie przez operatora i wykluczone ze statystyk warunkowych.",
                    PdfStyleHelper.getFooterFont());
            hypothesesFootnote.setSpacingBefore(6);
            hypothesesFootnote.setSpacingAfter(15);
            document.add(hypothesesFootnote);
        }

        // Nowa strona na kolejną sekcję
        document.newPage();
    }

    /**
     * Tworzy poziomy bar tabeli reprezentujący segmenty czasowe proporcjonalnie do czasu trwania.
     */
    private PdfPTable createTimelineBar(List<MeasurementSegment> segments) {
        LocalDateTime minStart = null;
        LocalDateTime maxEnd = null;
        for (MeasurementSegment seg : segments) {
            if (minStart == null || seg.getFromTimestamp().isBefore(minStart)) {
                minStart = seg.getFromTimestamp();
            }
            if (maxEnd == null || seg.getToTimestamp().isAfter(maxEnd)) {
                maxEnd = seg.getToTimestamp();
            }
        }

        long totalMs = 0;
        if (minStart != null && maxEnd != null) {
            totalMs = java.time.Duration.between(minStart, maxEnd).toMillis();
        }

        if (totalMs <= 0) {
            PdfPTable single = new PdfPTable(1);
            single.setWidthPercentage(100);
            single.addCell(PdfStyleHelper.createCell("Brak długości", PdfStyleHelper.getFooterFont(), Color.WHITE, Element.ALIGN_CENTER));
            return single;
        }

        float[] widths = new float[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            MeasurementSegment seg = segments.get(i);
            long dur = java.time.Duration.between(seg.getFromTimestamp(), seg.getToTimestamp()).toMillis();
            widths[i] = dur > 0 ? (float) dur : 1.0f;
        }

        PdfPTable barTable = new PdfPTable(widths.length);
        barTable.setWidthPercentage(100);
        try {
            barTable.setWidths(widths);
        } catch (DocumentException e) {
            // fallback na równe
        }

        for (MeasurementSegment seg : segments) {
            long dur = java.time.Duration.between(seg.getFromTimestamp(), seg.getToTimestamp()).toMillis();
            double percent = (double) dur / totalMs * 100.0;
            String text = "";
            if (percent >= 10.0) {
                text = getShortSegmentTypeLabel(seg.getType());
            }

            PdfPCell cell = new PdfPCell(new Phrase(text, PdfStyleHelper.getFooterFont()));
            cell.setBackgroundColor(getSegmentColor(seg.getType()));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(3);
            cell.setBorderWidth(0.5f);
            cell.setBorderColor(Color.WHITE);
            barTable.addCell(cell);
        }

        return barTable;
    }

    private void renderTimelineLegend(Document document) throws DocumentException {
        PdfPTable legend = new PdfPTable(4);
        legend.setWidthPercentage(100);
        legend.setWidths(new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        legend.setSpacingAfter(15);

        legend.addCell(PdfStyleHelper.createCell("STEADY_STATE (Ustalony)", PdfStyleHelper.getFooterFont(), getSegmentColor(SegmentType.STEADY_STATE), Element.ALIGN_CENTER));
        legend.addCell(PdfStyleHelper.createCell("EQUILIBRATION (Rozruch)", PdfStyleHelper.getFooterFont(), getSegmentColor(SegmentType.EQUILIBRATION), Element.ALIGN_CENTER));
        legend.addCell(PdfStyleHelper.createCell("SETPOINT_CHANGE (CUSUM)", PdfStyleHelper.getFooterFont(), getSegmentColor(SegmentType.SETPOINT_CHANGE), Element.ALIGN_CENTER));
        legend.addCell(PdfStyleHelper.createCell("DEFROST/DOOR (Zdarzenia)", PdfStyleHelper.getFooterFont(), getSegmentColor(SegmentType.DEFROST), Element.ALIGN_CENTER));

        document.add(legend);
    }

    private Color getSegmentColor(SegmentType type) {
        return switch (type) {
            case STEADY_STATE     -> new Color(220, 252, 231); // Green 100
            case EQUILIBRATION     -> new Color(254, 243, 199); // Amber 100
            case DEFROST          -> new Color(219, 234, 254); // Blue 100
            case DOOR_EVENT       -> new Color(243, 244, 246); // Gray 100
            case SETPOINT_CHANGE  -> new Color(243, 232, 255); // Purple 100
            case EXCURSION        -> new Color(254, 226, 226); // Red 100
            case NORMAL_USE       -> new Color(226, 232, 240); // Slate 200
        };
    }

    private String getShortSegmentTypeLabel(SegmentType type) {
        return switch (type) {
            case STEADY_STATE     -> "STEADY";
            case EQUILIBRATION     -> "EQUIL";
            case DEFROST          -> "DFRST";
            case DOOR_EVENT       -> "DOOR";
            case SETPOINT_CHANGE  -> "SHIFT";
            case EXCURSION        -> "EXCUR";
            case NORMAL_USE       -> "NORM";
        };
    }

    private String getEventTypeName(SegmentType type) {
        return switch (type) {
            case DEFROST          -> "Defrost";
            case DOOR_EVENT       -> "Otwarcie drzwi";
            case SETPOINT_CHANGE  -> "Zmiana nastawy (CUSUM)";
            case EXCURSION        -> "Ekskursja";
            default               -> type.name();
        };
    }
}
