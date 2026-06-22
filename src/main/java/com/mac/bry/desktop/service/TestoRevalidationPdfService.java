package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.RevalidationReportPdfRenderer;
import com.mac.bry.desktop.service.pdf.IndividualChartPdfRenderer;
import com.mac.bry.desktop.service.pdf.CalibrationCertificatePdfRenderer;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * Serwis fasady do generowania raportów GxP, wykresów serii pomiarowych oraz certyfikatów wzorcowania w formacie PDF.
 * Deleguje zadania do dedykowanych rendererów w celu zachowania zasady pojedynczej odpowiedzialności (SRP).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestoRevalidationPdfService {

    private final ValidationPlanNumberRepository validationPlanNumberRepository;
    private final HypothesisTestingService hypothesisTestingService;
    private final MetrologicalStatsService metrologicalStatsService;
    private final com.mac.bry.desktop.service.regime.RegimeDetectionService regimeDetectionService;
    private final com.mac.bry.desktop.service.regime.RegimeAwareStatsService regimeAwareStatsService;
    private final com.mac.bry.desktop.config.RegimeDetectionProperties regimeDetectionProperties;

    /**
     * Generuje zintegrowany raport z rewalidacji komory chłodniczej GxP w formacie PDF.
     */
    public void generateRevalidationReport(RevalidationSession session, File outputFile, File chartImageFile) throws IOException {
        log.info("Delegowanie generowania zintegrowanego raportu PDF: {}", outputFile.getAbsolutePath());
        new RevalidationReportPdfRenderer(
                metrologicalStatsService,
                regimeDetectionService,
                regimeAwareStatsService,
                regimeDetectionProperties
        ).render(
                session,
                outputFile,
                chartImageFile,
                hypothesisTestingService,
                validationPlanNumberRepository
        );
    }

    /**
     * Generuje indywidualny wykres przebiegu temperatury dla pojedynczej serii pomiarowej w formacie PDF.
     */
    public void generateIndividualSeriesChartPdf(RevalidationSession.GridPosition position, RevalidationSession.PositionData data, File outputFile) throws IOException {
        log.info("Delegowanie generowania indywidualnego wykresu PDF dla pozycji: {}", position.getLabel());
        new IndividualChartPdfRenderer().render(position, data, outputFile);
    }

    /**
     * Generuje cyfrowy certyfikat wzorcowania dla podanego obiektu wzorcowania (Calibration) w formacie PDF.
     */
    public void generateMockCertificatePdf(Calibration calibration, File outputFile) throws IOException {
        log.info("Delegowanie generowania makiety certyfikatu PDF: {}", calibration.getCertificateNumber());
        new CalibrationCertificatePdfRenderer().render(calibration, outputFile);
    }

    /**
     * Zwraca krótki kod pozycji siatki (np. G-PL) na potrzeby bindowania tabel i ZIP-owania.
     */
    public String getShortCode(RevalidationSession.GridPosition pos) {
        return PdfStyleHelper.getShortCode(pos);
    }
}
