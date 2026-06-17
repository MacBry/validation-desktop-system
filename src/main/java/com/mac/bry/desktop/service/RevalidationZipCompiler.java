package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.RevalidationSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serwis odpowiedzialny za kompilację paczki ZIP z dokumentami GxP dla sesji rewalidacji.
 *
 * Pakuje:
 *  - główny raport PDF sesji (zintegrowany z wykresem wielokanałowym)
 *  - Załącznik nr 8 Word (DOCX) ze schematem rozmieszczenia rejestratorów
 *  - indywidualne wykresy PDF dla każdej aktywnej pozycji siatki (folder wykresy/)
 *  - świadectwa wzorcowania dla każdego rejestratora (folder certyfikaty/)
 *
 * Wydzielony z TestoRevalidationController w celu zgodności z zasadą SRP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevalidationZipCompiler {

    private final TestoRevalidationPdfService testoRevalidationPdfService;
    private final TestoRevalidationWordService testoRevalidationWordService;
    private final TestoPdfReportService testoPdfReportService;
    private final JavaFxChartRenderer chartRenderer;

    /**
     * Kompiluje kompletny pakiet walidacyjny GxP do pliku ZIP.
     *
     * @param session       sesja rewalidacji z danymi pomiarowymi
     * @param mainChartPng  plik PNG z wyrenderowanym wykresem wielokanałowym (zrzut z UI)
     * @param outputZip     docelowy plik ZIP
     * @throws Exception    w przypadku błędu I/O lub generowania dokumentów
     */
    public void compile(RevalidationSession session, File mainChartPng, File outputZip) throws Exception {
        log.info("Rozpoczęcie kompilacji pakietu ZIP: {}", outputZip.getAbsolutePath());

        List<File> tempFilesToClean = new ArrayList<>();
        File tempPdfFile = null;
        File tempDocxFile = null;
        File tempDocxFile3 = null;
        File tempDocxFile7 = null;

        try {
            // 1. Główny raport PDF sesji
            tempPdfFile = File.createTempFile("raport_rewalidacji_", ".pdf");
            testoRevalidationPdfService.generateRevalidationReport(session, tempPdfFile, mainChartPng);
            log.info("Główny raport PDF wygenerowany: {}", tempPdfFile.getName());

            // 2. Załącznik nr 8 (Word / DOCX)
            tempDocxFile = File.createTempFile("Zalacznik_nr_8_rozmieszczenie_", ".docx");
            try (FileOutputStream fos = new FileOutputStream(tempDocxFile)) {
                testoRevalidationWordService.generateAppendix8(session, fos);
            }
            log.info("Załącznik nr 8 DOCX wygenerowany: {}", tempDocxFile.getName());

            // 2b. Załącznik nr 7 (dla MAPPING) lub Załącznik nr 3 (dla REVALIDATION)
            if (session.getProcedureType() == GxPProcedureType.MAPPING) {
                tempDocxFile7 = File.createTempFile("Zalacznik_nr_7_mapowanie_", ".docx");
                try (FileOutputStream fos = new FileOutputStream(tempDocxFile7)) {
                    testoRevalidationWordService.generateAppendix7(session, fos);
                }
                log.info("Załącznik nr 7 DOCX wygenerowany: {}", tempDocxFile7.getName());
            } else {
                tempDocxFile3 = File.createTempFile("Zalacznik_nr_3_raport_", ".docx");
                try (FileOutputStream fos = new FileOutputStream(tempDocxFile3)) {
                    testoRevalidationWordService.generateAppendix3(session, fos);
                }
                log.info("Załącznik nr 3 DOCX wygenerowany: {}", tempDocxFile3.getName());
            }

            // 3. Budowanie archiwum ZIP
            try (FileOutputStream zipFos = new FileOutputStream(outputZip);
                 ZipOutputStream zos = new ZipOutputStream(zipFos)) {

                addFileToZip(zos, tempPdfFile,
                        "Raport_Rewalidacji_GxP_" + session.getCoolingDevice().getInventoryNumber() + ".pdf");

                addFileToZip(zos, tempDocxFile,
                        "Zalacznik_nr_8_Graficzny_schemat_rozmieszczenia_rejestratorow.docx");

                if (session.getProcedureType() == GxPProcedureType.MAPPING) {
                    addFileToZip(zos, tempDocxFile7,
                            "Zalacznik_nr_7_Protokol_wykonania_mapowania_urzadzenia.docx");
                } else {
                    addFileToZip(zos, tempDocxFile3,
                            "Zalacznik_nr_3_Raport_z_walidacji_procesu_przechowywania.docx");
                }

                // 4. Indywidualne wykresy + certyfikaty dla każdej pozycji
                for (var entry : session.getAssignedPositions().entrySet()) {
                    RevalidationSession.GridPosition pos = entry.getKey();
                    RevalidationSession.PositionData data = entry.getValue();
                    String shortCode = testoRevalidationPdfService.getShortCode(pos);
                    String sn = data.getSerialNumber();

                    // 4a. Indywidualny wykres PDF (taki sam jak pojedynczy odczyt Testo)
                    File tempChartPng = chartRenderer.renderSeriesToPng(data.getSeries().getMeasurements());
                    tempFilesToClean.add(tempChartPng);

                    File tempIndividualPdf = File.createTempFile("temp_chart_" + shortCode + "_", ".pdf");
                    tempFilesToClean.add(tempIndividualPdf);

                    TestoPdfReportService.TestoReportData reportData = buildReportData(pos, data);
                    testoPdfReportService.generatePdfReport(reportData, tempIndividualPdf, tempChartPng);

                    addFileToZip(zos, tempIndividualPdf,
                            "wykresy/Wykres_serii_" + shortCode + "_" + sn + ".pdf");

                    // 4b. Świadectwo wzorcowania
                    Calibration cal = data.getLatestCalibration();
                    if (cal != null) {
                        File certFile = resolveCertificateFile(cal, sn, tempFilesToClean);
                        String certNo = cal.getCertificateNumber().replaceAll("[^a-zA-Z0-9_-]", "_");
                        addFileToZip(zos, certFile,
                                "certyfikaty/Certyfikat_wzorcowania_" + sn + "_" + certNo + ".pdf");
                    }
                }
            }

            log.info("Pakiet ZIP skompilowany pomyślnie: {} ({} KB)",
                    outputZip.getName(), outputZip.length() / 1024);

        } finally {
            cleanupTempFiles(tempPdfFile, tempDocxFile, tempDocxFile3, tempDocxFile7);
            cleanupTempFiles(tempFilesToClean.toArray(new File[0]));
        }
    }

    // ---- Helpers ----

    private TestoPdfReportService.TestoReportData buildReportData(
            RevalidationSession.GridPosition pos, RevalidationSession.PositionData data) {
        TestoPdfReportService.TestoReportData rd = new TestoPdfReportService.TestoReportData();
        rd.model = data.getModel() != null ? data.getModel().getName() : "Nieznany";
        rd.serialNumber = data.getSerialNumber();
        rd.batteryLevel = data.getSeries().getBatteryLevelPercent() + "%";
        rd.interval = data.getSeries().getLoggingIntervalMinutes() + " minut";
        Integer startDelay = data.getSeries().getStartDelayMinutes();
        rd.startDelay = (startDelay != null && startDelay > 0)
                ? startDelay + " minut"
                : "Brak opóźnienia";
        rd.comments = "Pozycja w komorze: " + pos.getLabel();
        rd.measurements = data.getSeries().getMeasurements();
        return rd;
    }

    private File resolveCertificateFile(Calibration cal, String sn, List<File> tempFiles) throws Exception {
        if (cal.getCertificateFilePath() != null) {
            File f = new File(cal.getCertificateFilePath());
            if (f.exists() && f.isFile()) return f;
        }
        // Brak pliku na dysku → generujemy makietę PDF świadectwa
        File tempCert = File.createTempFile("temp_cert_" + sn + "_", ".pdf");
        tempFiles.add(tempCert);
        testoRevalidationPdfService.generateMockCertificatePdf(cal, tempCert);
        log.debug("Wygenerowano makietę świadectwa wzorcowania dla S/N: {}", sn);
        return tempCert;
    }

    private void addFileToZip(ZipOutputStream zos, File file, String zipPath) throws IOException {
        ZipEntry entry = new ZipEntry(zipPath);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.transferTo(zos);
        }
        zos.closeEntry();
        log.debug("Dodano do ZIP: {}", zipPath);
    }

    private void cleanupTempFiles(File... files) {
        for (File f : files) {
            if (f != null && f.exists()) {
                boolean deleted = f.delete();
                if (!deleted) log.warn("Nie udało się usunąć pliku tymczasowego: {}", f.getAbsolutePath());
            }
        }
    }
}
