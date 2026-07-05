package com.mac.bry.desktop.service.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.MetrologicalStatsService;
import com.mac.bry.desktop.service.PdfHeaderFooterHandler;
import com.mac.bry.desktop.service.pdf.section.*;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import com.mac.bry.desktop.service.regime.RegimeDetectionService;
import com.mac.bry.desktop.service.regime.RegimeAwareStatsService;
import com.mac.bry.desktop.config.RegimeDetectionProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;

@Slf4j
public class RevalidationReportPdfRenderer {

    private final List<PdfSectionRenderer> sectionRenderers = new ArrayList<>();
    private final MetrologicalStatsService metrologicalStatsService;
    private final RegimeDetectionService regimeDetectionService;
    private final RegimeAwareStatsService regimeAwareStatsService;

    public RevalidationReportPdfRenderer(
            MetrologicalStatsService metrologicalStatsService,
            RegimeDetectionService regimeDetectionService,
            RegimeAwareStatsService regimeAwareStatsService,
            RegimeDetectionProperties regimeDetectionProperties) {
        this.metrologicalStatsService = metrologicalStatsService;
        this.regimeDetectionService = regimeDetectionService;
        this.regimeAwareStatsService = regimeAwareStatsService;
        // Rejestracja sekcji w odpowiedniej kolejności
        sectionRenderers.add(new TitleAndChamberSectionRenderer());
        sectionRenderers.add(new TraceabilitySectionRenderer());
        if (regimeDetectionProperties.isEnabled()) {
            sectionRenderers.add(new RegimeAwareSectionRenderer());
        }
        sectionRenderers.add(new MetrologicalSectionRenderer());
        sectionRenderers.add(new StatisticalSectionRenderer());
        sectionRenderers.add(new ShewhartSectionRenderer());
        sectionRenderers.add(new MeasurementMatrixSectionRenderer());
    }

    public void render(RevalidationSession session, File outputFile, File chartImageFile, 
                       HypothesisTestingService hypothesisTestingService, 
                       ValidationPlanNumberRepository validationPlanNumberRepository) throws IOException {
        log.info("Rozpoczęcie kompilacji zintegrowanego raportu PDF: {}", outputFile.getAbsolutePath());

        // 1. Sortowanie aktywnych pozycji celem zachowania spójności kolumn w tabeli
        List<RevalidationSession.GridPosition> activePositions = new ArrayList<>(session.getAssignedPositions().keySet());
        Collections.sort(activePositions);

        // 1b. Pre-compute skorygowanych statystyk wzorcowania per pozycja
        CoolingChamber chamber = session.getCoolingChamber();
        Double lsl = (chamber != null) ? chamber.getMinOperatingTemp() : null;
        Double usl = (chamber != null) ? chamber.getMaxOperatingTemp() : null;
        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            if (d != null && d.getSeries() != null) {
                var correctedDto = metrologicalStatsService.calculateCorrectedStatistics(
                        d.getSeries(), d.getLatestCalibration(), lsl, usl);
                session.getCorrectedStatsMap().put(pos, correctedDto);
            }
        }
        log.info("Pre-compute skorygowanych statystyk zakończony dla {} pozycji.", activePositions.size());

        // 1c. Pre-compute segmentacji i statystyk warunkowych
        Map<RevalidationSession.GridPosition, ThermoMeasurementSeries> allChannels = new HashMap<>();
        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            if (d != null && d.getSeries() != null) {
                allChannels.put(pos, d.getSeries());
            }
        }

        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            if (d != null && d.getSeries() != null) {
                // Reuse segmentów z sesji, jeśli operator już je przeglądał (Faza 4) —
                // ponowna detekcja nadpisałaby adnotacje human-in-the-loop
                var segments = session.getDetectedSegmentsMap().get(pos);
                if (segments == null) {
                    var detection = regimeDetectionService.detect(d.getSeries(), allChannels);
                    segments = detection.getSegments();
                    session.getDetectedSegmentsMap().put(pos, segments);
                }

                var conditionalDto = regimeAwareStatsService.calculateConditionalStatistics(
                        d.getSeries(), segments, session.getRunMode(), lsl, usl);
                session.getConditionalStatsMap().put(pos, conditionalDto);
            }
        }
        log.info("Pre-compute segmentacji i statystyk warunkowych zakończony.");

        // Pobranie numeru RPW (dla metadanych)
        String rpwFormatted = "–";
        if (validationPlanNumberRepository != null && session.getCoolingDevice() != null) {
            var planNumbers = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(session.getCoolingDevice());
            if (!planNumbers.isEmpty()) {
                rpwFormatted = planNumbers.get(0).getFormattedRpw();
            }
        }

        // 2. Obliczenie kryptograficznej sumy kontrolnej SHA-256 z całej macierzy pomiarowej (FDA 21 CFR Part 11)
        String checksum = ReportChecksumHelper.calculateSha256Checksum(session, activePositions);
        log.info("Zintegrowana suma kontrolna SHA-256 sesji rewalidacji: {}", checksum);

        try (Document document = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));

            // Czcionki z obsługą polskich znaków (Arial) pobrane z PdfStyleHelper
            BaseFont baseFont = PdfStyleHelper.getBaseFont();
            Font footerFont = PdfStyleHelper.getFooterFont();

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

            // Renderowanie wszystkich zarejestrowanych sekcji
            for (PdfSectionRenderer renderer : sectionRenderers) {
                renderer.renderSection(document, session, activePositions, hypothesisTestingService, validationPlanNumberRepository, chartImageFile, checksum);
            }

        } catch (Exception e) {
            log.error("Błąd kompilacji raportu GxP PDF", e);
            throw new IOException("Błąd podczas kompilacji raportu PDF: " + e.getMessage(), e);
        }
    }
}
