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
            if (isH2Database()) {
                executeH2Backup();
                cleanOldBackups();
            } else if (isMysqlDumpAvailable()) {
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

    private boolean isH2Database() {
        return dbUrl != null && dbUrl.startsWith("jdbc:h2:");
    }

    /**
     * Backup trybu standalone: natywne polecenie H2 {@code BACKUP TO} —
     * spójny zrzut online całej bazy do archiwum ZIP, bez zewnętrznych narzędzi.
     */
    public void executeH2Backup() throws java.sql.SQLException, IOException {
        Path backupPath = Paths.get(backupDir);
        if (!Files.exists(backupPath)) {
            Files.createDirectories(backupPath);
        }

        String fileName = String.format("db_backup_h2_%s.zip", LocalDateTime.now().format(FILE_NAME_FORMAT));
        File outputFile = backupPath.resolve(fileName).toFile();

        log.info("Generowanie backupu H2 (BACKUP TO): {}", outputFile.getAbsolutePath());
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("BACKUP TO '" + outputFile.getAbsolutePath().replace("'", "''") + "'");
        }
        log.info("Backup H2 zakończony sukcesem: {}", fileName);
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

        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(mysqlDumpPath);
        command.add("--user=" + dbUsername);
        if (dbPassword != null && !dbPassword.trim().isEmpty()) {
            command.add("--password=" + dbPassword);
        }
        command.add("--databases");
        command.add(dbName);
        command.add("--result-file=" + outputFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Odczytanie wyjścia żeby proces nie zawiesił się na pełnym buforze
        // oraz by wypisać ewentualny błąd z mysqldump
        String output;
        try (java.util.Scanner scanner = new java.util.Scanner(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8).useDelimiter("\\A")) {
            output = scanner.hasNext() ? scanner.next() : "";
        }
        
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            log.info("Backup zakończony sukcesem: {}", fileName);
        } else {
            log.error("mysqldump zakończony błędem (kod: {}). Wyjście: {}", exitCode, output);
            throw new IOException("mysqldump failed with exit code " + exitCode + ". Output: " + output);
        }
    }

    private void cleanOldBackups() throws IOException {
        log.info("Czyszczenie backupów starszych niż {} dni...", retentionDays);
        Path backupPath = Paths.get(backupDir);
        
        if (!Files.exists(backupPath)) return;

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        Files.list(backupPath)
                .filter(path -> path.toString().endsWith(".sql") || path.toString().endsWith(".zip"))
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
