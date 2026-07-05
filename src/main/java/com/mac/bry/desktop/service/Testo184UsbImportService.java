package com.mac.bry.desktop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.bry.desktop.service.helper.PythonBridgeRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class Testo184UsbImportService {

    private static final Duration BRIDGE_TIMEOUT = Duration.ofSeconds(30);

    private final PythonBridgeRunner bridgeRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Odczytuje surowe dane oraz metadane z pliku PDF raportu Testo 184T za pomocą mostu Python.
     */
    public TestoUsbImportService.TestoImportResult importFromPdf(File pdfFile) {
        log.info("Rozpoczęcie importu pomiarów z pliku PDF Testo 184: {}", pdfFile.getAbsolutePath());
        TestoUsbImportService.TestoImportResult result = new TestoUsbImportService.TestoImportResult();

        try {
            // Wypakowywanie skryptu uruchomieniowego oraz biblioteki do wspólnego folderu tymczasowego
            File readerScript = getOrExtractResource("testo/testo_184_reader_bridge.py", "testo_184_reader_bridge.py");
            File readerLibrary = getOrExtractResource("testo/testo_184_pdf_reader.py", "testo_184_pdf_reader.py");

            if (readerScript == null || !readerScript.exists() || readerLibrary == null || !readerLibrary.exists()) {
                result.status = "ERROR";
                result.message = "Nie znaleziono skryptów odczytu Testo 184 w zasobach.";
                return result;
            }

            PythonBridgeRunner.BridgeResult bridge = bridgeRunner.run(
                    readerScript, List.of(pdfFile.getAbsolutePath()), BRIDGE_TIMEOUT);

            if (bridge.timedOut()) {
                result.status = "ERROR";
                result.message = "Przekroczono czas oczekiwania (" + BRIDGE_TIMEOUT.toSeconds()
                        + "s) na proces odczytu PDF Testo 184T.";
                return result;
            }

            log.info("Proces odczytu zakończony z kodem {}. Rozmiar stdout: {} znaków.",
                    bridge.exitCode(), bridge.stdout().length());

            if (bridge.stdout().isEmpty()) {
                result.status = "ERROR";
                result.message = "Skrypt Python nie zwrócił żadnych danych na wyjście standardowe.";
                return result;
            }

            // Deserializacja Jacksonem
            try {
                return objectMapper.readValue(bridge.stdout(), TestoUsbImportService.TestoImportResult.class);
            } catch (Exception parseException) {
                log.error("Nie udało się sparsować JSON odczytanego ze skryptu Python PDF. Wyjście:\n{}",
                        bridge.stdout(), parseException);
                result.status = "ERROR";
                result.message = "Błąd parsowania odpowiedzi JSON ze skryptu PDF: " + parseException.getMessage();
                return result;
            }

        } catch (Exception e) {
            log.error("Wystąpił krytyczny błąd podczas wywołania mostu Python dla odczytu PDF", e);
            result.status = "ERROR";
            result.message = "Krytyczny błąd odczytu raportu PDF: " + e.getMessage();
            return result;
        }
    }

    private File getOrExtractResource(String classpathPath, String targetFileName) {
        try {
            // Wersja developerska
            File localFile = new File("src/main/resources/" + classpathPath);
            if (localFile.exists()) {
                return localFile;
            }

            // Wersja spakowana w JAR
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "vcc_testo_184_reader";
            File dir = new File(tempDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File targetFile = new File(dir, targetFileName);
            ClassPathResource resource = new ClassPathResource(classpathPath);
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return targetFile;
        } catch (Exception e) {
            log.error("Błąd podczas wypakowywania zasobu: {}", classpathPath, e);
            return null;
        }
    }
}
