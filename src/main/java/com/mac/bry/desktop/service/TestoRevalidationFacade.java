package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.dto.stats.AnovaResult;
import com.mac.bry.desktop.dto.stats.TostResult;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.repository.CoolingDeviceRepository;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javafx.scene.chart.LineChart;
import java.io.File;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestoRevalidationFacade {

    private final TestoRevalidationService revalidationService;
    private final CoolingDeviceRepository coolingDeviceRepository;
    private final CoolingChamberRepository coolingChamberRepository;
    private final JavaFxChartRenderer chartRenderer;
    private final RevalidationZipCompiler zipCompiler;
    private final Testo184UsbImportService testo184UsbImportService;
    private final HypothesisTestingService hypothesisTestingService;

    // --- REPOZYTORIA ---
    public List<CoolingDevice> findAllDevices() {
        return coolingDeviceRepository.findAll();
    }

    public List<CoolingChamber> findChambersByDeviceId(Long deviceId) {
        return coolingChamberRepository.findByCoolingDeviceId(deviceId);
    }

    // --- ZARZĄDZANIE SESJĄ ---
    public RevalidationSession initSession(CoolingDevice device, CoolingChamber chamber, GxPProcedureType type) {
        return revalidationService.initSession(device, chamber, type);
    }

    public void saveSession(RevalidationSession session) {
        revalidationService.saveRevalidationSession(session);
    }

    // --- ODCZYT DANYCH SENSORÓW ---
    public RevalidationSession.PositionData readPositionData(RevalidationSession session, RevalidationSession.GridPosition pos, boolean simulate) throws Exception {
        return revalidationService.readPositionData(session, pos, simulate);
    }

    public RevalidationSession.PositionData readPositionData(RevalidationSession session, RevalidationSession.GridPosition pos, boolean simulate, SimulationProfile profile) throws Exception {
        return revalidationService.readPositionData(session, pos, simulate, profile);
    }

    public RevalidationSession.PositionData readPositionDataFromPdf(RevalidationSession session, RevalidationSession.GridPosition pos, File pdfFile) throws Exception {
        return revalidationService.readPositionDataFromPdf(session, pos, pdfFile);
    }

    // --- WYKRESY I RAPORTY ---
    public File snapshotExistingChart(LineChart<Number, Number> chart) throws Exception {
        return chartRenderer.snapshotExistingChart(chart);
    }

    public void compileZip(RevalidationSession session, File chartPng, File outputZip) throws Exception {
        zipCompiler.compile(session, chartPng, outputZip);
    }

    // --- TESTOWANIE HIPOTEZ GxP ---
    public TostResult performTostEquivalence(double[] sample1, double[] sample2, double theta) {
        return hypothesisTestingService.performTostEquivalence(sample1, sample2, theta);
    }

    public double performFTest(double[] sample1, double[] sample2) {
        return hypothesisTestingService.performFTest(sample1, sample2);
    }

    public AnovaResult performAnova(List<double[]> samples) {
        return hypothesisTestingService.performAnova(samples);
    }

    public double performKruskalWallis(List<double[]> samples) {
        return hypothesisTestingService.performKruskalWallis(samples);
    }

    public double performJarqueBera(double[] sample) {
        return hypothesisTestingService.performJarqueBera(sample);
    }
}
