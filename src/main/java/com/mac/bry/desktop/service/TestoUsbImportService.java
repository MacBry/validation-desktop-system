package com.mac.bry.desktop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TestoUsbImportService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Główny DTO reprezentujący wynik importu USB z rejestratora.
     */
    public static class TestoImportResult {
        public String status;
        public String message;
        public DeviceInfo device;
        public SessionInfo session;
        public List<MeasurementPointDto> measurements = new ArrayList<>();

        @Override
        public String toString() {
            return "TestoImportResult{status='" + status + '\'' +
                    ", device=" + (device != null ? device.serialNumber : "null") +
                    ", measurementsCount=" + (measurements != null ? measurements.size() : 0) +
                    '}';
        }
    }

    public static class DeviceInfo {
        public String model;
        public String serialNumber;
        public String manufacturingDate;
    }

    public static class SessionInfo {
        public int batteryLevelPercent;
        public int intervalMinutes;
        public int measurementsCount;
        public String programmingTimeUtc;
        public int startDelayMinutes;
        public String firstMeasurementTimeUtc;
        public String firstMeasurementTimeLocal;
    }

    public static class MeasurementPointDto {
        public int index;
        public String timestampLocal;
        public double valueCelsius;
    }

    /**
     * Odczytuje dane bezpośrednio z kołyski Testo USB za pośrednictwem mostu Python.
     */
    public TestoImportResult readFromUsb() {
        log.info("Rozpoczęcie wywołania procesu USB mostu Python.");
        TestoImportResult result = new TestoImportResult();

        try {
            // Lokalizowanie lub wypakowywanie skryptu i konfiguracji
            File scriptFile = getOrExtractResource("testo/testo_usb_reader.py", "testo_usb_reader.py");
            File configFile = getOrExtractResource("testo/testo_config.yml", "testo_config.yml");

            if (scriptFile == null || !scriptFile.exists()) {
                result.status = "ERROR";
                result.message = "Nie znaleziono skryptu testo_usb_reader.py w zasobach.";
                return result;
            }

            // Konfiguracja ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder("python", scriptFile.getAbsolutePath(), "--json");
            pb.directory(scriptFile.getParentFile());
            pb.redirectErrorStream(true);

            log.info("Uruchamianie procesu Python: {} z parametrem --json", scriptFile.getAbsolutePath());
            Process process = pb.start();

            // Czytanie strumienia stdout
            StringBuilder jsonOutput = new StringBuilder();
            try (InputStream is = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    jsonOutput.append(new String(buffer, 0, bytesRead));
                }
            }

            // Oczekiwanie na zakończenie procesu (max 10 sekund)
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.status = "ERROR";
                result.message = "Przekroczono limit czasu oczekiwania (10s) na odczyt USB Testo.";
                return result;
            }

            int exitCode = process.exitValue();
            String outputStr = jsonOutput.toString().trim();
            log.info("Proces zakończony z kodem: {}. Rozmiar stdout: {} bajtów.", exitCode, outputStr.length());

            if (outputStr.isEmpty()) {
                result.status = "ERROR";
                result.message = "Skrypt Python nie zwrócił żadnych danych na wyjście standardowe.";
                return result;
            }

            // Parsowanie wyniku JSON za pomocą Jacksona
            try {
                return objectMapper.readValue(outputStr, TestoImportResult.class);
            } catch (Exception parseException) {
                log.error("Nie udało się sparsować JSON odczytanego ze skryptu Python. Wyjście: {}", outputStr, parseException);
                result.status = "ERROR";
                result.message = "Błąd parsowania odpowiedzi JSON ze skryptu USB: " + parseException.getMessage();
                return result;
            }

        } catch (Exception e) {
            log.error("Wystąpił krytyczny błąd podczas wywołania mostu Python USB", e);
            result.status = "ERROR";
            result.message = "Krytyczny błąd wywołania sterownika USB: " + e.getMessage();
            return result;
        }
    }

    /**
     * Wyszukuje plik w lokalnym drzewie katalogów projektu (tryb developerski) 
     * lub wypakowuje go z zasobów JAR do katalogu tymczasowego systemowego (tryb produkcyjny).
     */
    private File getOrExtractResource(String classpathPath, String targetFileName) {
        try {
            // Wersja developerska: Sprawdzamy lokalną ścieżkę w workspace
            File localFile = new File("src/main/resources/" + classpathPath);
            if (localFile.exists()) {
                log.debug("Znaleziono plik lokalny w workspace: {}", localFile.getAbsolutePath());
                return localFile;
            }

            // Wersja wdrożeniowa (packaged JAR): Wypakowujemy z classpath do katalogu tymczasowego
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "vcc_testo_reader";
            File dir = new File(tempDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File targetFile = new File(dir, targetFileName);
            log.debug("Wypakowywanie zasobu z JAR do: {}", targetFile.getAbsolutePath());

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
