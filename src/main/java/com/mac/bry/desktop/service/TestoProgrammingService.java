package com.mac.bry.desktop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TestoProgrammingService {

    /**
     * Główna metoda programująca loger Testo przez USB.
     * Wywołuje skrypt Pythonowy testo_usb_programmer.py
     *
     * @param intervalMinutes Interwał w minutach
     * @param count           Liczba pomiarów
     * @param startLocalTime  Czas rozpoczęcia pierwszego pomiaru (czas lokalny Polski)
     * @param upperLimit      Górny próg alarmowy
     * @param lowerLimit      Dolny próg alarmowy
     * @return true, jeśli sprzęt zwrócił ACK (potwierdzenie), false w przeciwnym razie.
     */
    public boolean programTestoLogger(int intervalMinutes, int count, LocalDateTime startLocalTime, double upperLimit, double lowerLimit) {
        log.info("Rozpoczęcie wywołania procesu programowania USB Testo.");

        try {
            // Lokalizowanie lub wypakowywanie skryptu
            File scriptFile = getOrExtractResource("testo/testo_usb_programmer.py", "testo_usb_programmer.py");

            if (scriptFile == null || !scriptFile.exists()) {
                log.error("Nie znaleziono skryptu testo_usb_programmer.py w zasobach.");
                return false;
            }

            // Formatowanie czasu dla argumentu linii komend
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startStr = startLocalTime.format(formatter);

            // Konfiguracja ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    scriptFile.getAbsolutePath(),
                    "--interval", String.valueOf(intervalMinutes),
                    "--count", String.valueOf(count),
                    "--start", startStr,
                    "--upper", String.valueOf(upperLimit),
                    "--lower", String.valueOf(lowerLimit)
            );
            
            pb.directory(scriptFile.getParentFile());
            pb.redirectErrorStream(true);

            log.info("Uruchamianie procesu Python: {} z parametrami interval={}, count={}, start={}", 
                    scriptFile.getAbsolutePath(), intervalMinutes, count, startStr);
            
            Process process = pb.start();

            // Czytanie strumienia stdout
            StringBuilder output = new StringBuilder();
            try (InputStream is = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, bytesRead));
                }
            }

            // Oczekiwanie na zakończenie procesu (max 20 sekund, bo urządzenie potrzebuje ~8s na flashowanie)
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Przekroczono limit czasu oczekiwania (20s) na programowanie USB Testo.");
                return false;
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            log.info("Proces programowania zakończony z kodem: {}. Wyjście:\n{}", exitCode, outputStr);

            // Skrypt Pythona wypisze "[OK]" jeśli rejestrator zwróci odpowiedź ACK ab 61
            if (outputStr.contains("[OK]")) {
                return true;
            } else {
                log.error("Skrypt Python nie zaraportował sukcesu. Szukano frazy '[OK]'.");
                return false;
            }

        } catch (Exception e) {
            log.error("Wystąpił krytyczny błąd podczas wywołania mostu Python dla programowania", e);
            return false;
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
                return localFile;
            }

            // Wersja wdrożeniowa (packaged JAR)
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "vcc_testo_programmer";
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
