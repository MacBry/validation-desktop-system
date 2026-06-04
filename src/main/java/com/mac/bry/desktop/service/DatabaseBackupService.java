package com.mac.bry.desktop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Service
@Slf4j
public class DatabaseBackupService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.backup.directory:backups/db}")
    private String backupDir;

    @Value("${app.backup.mysql-dump-path:mysqldump}")
    private String mysqlDumpPath;

    @Value("${app.backup.retention-days:14}")
    private int retentionDays;

    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    /**
     * Uruchamia backup co 12 godzin (43 200 000 ms)
     */
    @Scheduled(fixedRate = 43200000)
    public void performScheduledBackup() {
        log.info("Rozpoczynanie zaplanowanego backupu bazy danych...");
        try {
            if (isMysqlDumpAvailable()) {
                executeBackup();
                cleanOldBackups();
            } else {
                log.warn("AUTOMATYCZNY BACKUP POMINIĘTY: Narzędzie 'mysqldump' nie jest zainstalowane lub nie ma go w PATH. " +
                        "Zainstaluj MySQL Client Utilities, aby włączyć tę funkcję.");
            }
        } catch (Exception e) {
            log.error("BŁĄD BACKUPU: Nieoczekiwany problem podczas tworzenia kopii zapasowej", e);
        }
    }

    private boolean isMysqlDumpAvailable() {
        try {
            Process process = new ProcessBuilder(mysqlDumpPath, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void executeBackup() throws IOException, InterruptedException {
        // Wyciąganie nazwy bazy z URL (jdbc:mysql://localhost:3306/db_name?...)
        String dbName = extractDbName(dbUrl);
        
        Path backupPath = Paths.get(backupDir);
        if (!Files.exists(backupPath)) {
            Files.createDirectories(backupPath);
        }

        String fileName = String.format("db_backup_%s_%s.sql", dbName, LocalDateTime.now().format(FILE_NAME_FORMAT));
        File outputFile = backupPath.resolve(fileName).toFile();

        log.info("Generowanie pliku backupu: {}", outputFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(
                mysqlDumpPath,
                "--user=" + dbUsername,
                "--password=" + dbPassword,
                "--databases", dbName,
                "--result-file=" + outputFile.getAbsolutePath()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            log.info("Backup zakończony sukcesem: {}", fileName);
        } else {
            log.error("mysqldump zakończony błędem (kod: {}). Sprawdź logi systemowe.", exitCode);
            throw new IOException("mysqldump failed with exit code " + exitCode);
        }
    }

    private void cleanOldBackups() throws IOException {
        log.info("Czyszczenie backupów starszych niż {} dni...", retentionDays);
        Path backupPath = Paths.get(backupDir);
        
        if (!Files.exists(backupPath)) return;

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        Files.list(backupPath)
                .filter(path -> path.toString().endsWith(".sql"))
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant().isBefore(threshold.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        log.info("Usunięto stary backup: {}", path.getFileName());
                    } catch (IOException e) {
                        log.warn("Nie udało się usunąć pliku: {}", path);
                    }
                });
    }

    private String extractDbName(String url) {
        try {
            // jdbc:mysql://localhost:3306/validation_desktop_db?...
            String cleanUrl = url.split("\\?")[0];
            return cleanUrl.substring(cleanUrl.lastIndexOf("/") + 1);
        } catch (Exception e) {
            log.warn("Nie udało się sparsować nazwy bazy z URL, używam 'default_db'");
            return "default_db";
        }
    }
}
