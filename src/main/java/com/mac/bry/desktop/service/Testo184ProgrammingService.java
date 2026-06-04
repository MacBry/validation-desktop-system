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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class Testo184ProgrammingService {

    /**
     * Uruchamia proces programowania rejestratora Testo 184T poprzez most Pythonowy.
     */
    public boolean programLogger(String driveLetter, int intervalMinutes, int count, LocalDateTime startLocalTime,
                                 int startMode, int startDelayMinutes, Double upperLimit, Integer upperMinutes,
                                 Double lowerLimit, Integer lowerMinutes, String operator, String comment) {
        log.info("Rozpoczęcie programowania rejestratora Testo 184T na dysku: {}", driveLetter);

        try {
            // Wypakowywanie skryptu programującego oraz biblioteki kodującej do wspólnego folderu
            File programmerScript = getOrExtractResource("testo/testo_184_programmer.py", "testo_184_programmer.py");
            File configLibrary = getOrExtractResource("testo/testo_184_config.py", "testo_184_config.py");

            if (programmerScript == null || !programmerScript.exists() || configLibrary == null || !configLibrary.exists()) {
                log.error("Nie znaleziono skryptów programowania Testo 184 w zasobach.");
                return false;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String startStr = startLocalTime.format(formatter);

            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(programmerScript.getAbsolutePath());
            command.add("--drive");
            command.add(driveLetter);
            command.add("--interval");
            command.add(String.valueOf(intervalMinutes));
            command.add("--count");
            command.add(String.valueOf(count));
            command.add("--start-mode");
            command.add(String.valueOf(startMode));
            command.add("--start-delay");
            command.add(String.valueOf(startDelayMinutes));
            command.add("--start-time");
            command.add(startStr);
            command.add("--operator");
            command.add(operator != null && !operator.trim().isEmpty() ? operator : "Validation System");

            if (comment != null && !comment.trim().isEmpty()) {
                command.add("--comment");
                command.add(comment);
            }

            if (upperLimit != null) {
                command.add("--upper-limit");
                command.add(String.valueOf(upperLimit));
                command.add("--upper-minutes");
                command.add(String.valueOf(upperMinutes != null ? upperMinutes : 60));
            }

            if (lowerLimit != null) {
                command.add("--lower-limit");
                command.add(String.valueOf(lowerLimit));
                command.add("--lower-minutes");
                command.add(String.valueOf(lowerMinutes != null ? lowerMinutes : 60));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(programmerScript.getParentFile());
            pb.redirectErrorStream(true);

            log.info("Uruchamianie procesu Python: {}", command);
            Process process = pb.start();

            // Odczyt wyjścia procesu
            StringBuilder output = new StringBuilder();
            try (InputStream is = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    output.append(new String(buffer, 0, bytesRead));
                }
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Przekroczono czas oczekiwania (15s) na proces programowania Testo 184T.");
                return false;
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            log.info("Proces programowania zakończony z kodem {}. Wyjście:\n{}", exitCode, outputStr);

            return exitCode == 0 && outputStr.contains("[OK]");

        } catch (Exception e) {
            log.error("Krytyczny błąd podczas programowania logera Testo 184T", e);
            return false;
        }
    }

    private File getOrExtractResource(String classpathPath, String targetFileName) {
        try {
            // Spróbowanie odczytania w trybie developerskim
            File localFile = new File("src/main/resources/" + classpathPath);
            if (localFile.exists()) {
                return localFile;
            }

            // Wersja spakowana JAR
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "vcc_testo_184_programmer";
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
