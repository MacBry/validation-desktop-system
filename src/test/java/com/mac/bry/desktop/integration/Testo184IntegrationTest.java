package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.service.Testo184ProgrammingService;
import com.mac.bry.desktop.service.Testo184UsbImportService;
import com.mac.bry.desktop.service.TestoUsbImportService.TestoImportResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class Testo184IntegrationTest {

    @Autowired
    private Testo184ProgrammingService programmingService;

    @Autowired
    private Testo184UsbImportService importService;

    @Test
    void shouldProgramTesto184LoggerSuccessfully(@TempDir Path tempDir) throws Exception {
        // Given
        String drivePath = tempDir.toAbsolutePath().toString();
        int intervalMinutes = 5;
        int count = 100;
        LocalDateTime startLocalTime = LocalDateTime.of(2026, 5, 25, 10, 0);
        boolean startModeManual = true;
        Double upperLimit = 8.0;
        Integer upperMinutes = 10;
        Double lowerLimit = 2.0;
        Integer lowerMinutes = 10;
        String operator = "JUnit Test Operator";
        String comment = "Integration Test Configuration";

        // When
        boolean success = programmingService.programLogger(
                drivePath, intervalMinutes, count, startLocalTime,
                3, 0, upperLimit, upperMinutes, lowerLimit, lowerMinutes,
                operator, comment
        );

        // Then
        assertTrue(success, "Programming service should return true on successful configuration.");

        Path expectedXmlFile = tempDir.resolve("testo 184 configuration_data.xml");
        assertThat(expectedXmlFile).exists();

        String xmlContent = Files.readString(expectedXmlFile).replace("\r", "");
        assertThat(xmlContent).contains("JUnit Test Operator");
        assertThat(xmlContent).contains("Integration Test Configuration");
        assertThat(xmlContent).contains("<measInterval\n>5</measInterval\n>");
        log.info("Programming test passed. XML configuration successfully generated and verified.");
    }

    @Test
    void shouldImportFromTesto184PdfReportSuccessfully() throws Exception {
        // Given
        ClassPathResource pdfResource = new ClassPathResource("testo_184_measurement_report.pdf");
        File pdfFile = pdfResource.getFile();
        assertThat(pdfFile).exists();

        // When
        TestoImportResult result = importService.importFromPdf(pdfFile);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status).isEqualTo("SUCCESS");
        assertThat(result.device).isNotNull();
        assertThat(result.device.model).isEqualTo("testo 184 T3");
        assertThat(result.device.serialNumber).isEqualTo("44373263");

        assertThat(result.session).isNotNull();
        assertThat(result.session.intervalMinutes).isEqualTo(1);
        assertThat(result.session.measurementsCount).isEqualTo(11);
        assertThat(result.session.firstMeasurementTimeLocal).isEqualTo("2026-05-22 15:16:00");

        assertThat(result.measurements).hasSize(11);
        
        // Assert first measurement point
        var firstPt = result.measurements.get(0);
        assertThat(firstPt.index).isEqualTo(1);
        assertThat(firstPt.timestampLocal).isEqualTo("2026-05-22T15:16:00");
        assertThat(firstPt.valueCelsius).isEqualTo(25.4958);

        // Assert last measurement point
        var lastPt = result.measurements.get(10);
        assertThat(lastPt.index).isEqualTo(11);
        assertThat(lastPt.timestampLocal).isEqualTo("2026-05-22T15:26:00");
        assertThat(lastPt.valueCelsius).isEqualTo(23.3004);

        log.info("PDF import test passed. Metadata and all 11 measurement points successfully parsed and verified.");
    }
}
