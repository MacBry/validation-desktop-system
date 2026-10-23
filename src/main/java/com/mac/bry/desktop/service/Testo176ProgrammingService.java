package com.mac.bry.desktop.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Programowanie rejestratora Testo 176 T4 przez most Pythonowy.
 * <p>
 * W odróżnieniu od {@link TestoProgrammingService} (174 T, kołyska FTDI D2XX)
 * i {@link Testo184ProgrammingService} (184 T3, pamięć masowa), 176 T4
 * komunikuje się przez <b>wirtualny port COM</b> sterownika {@code testousbser}.
 * Protokół opisuje {@code docs/TESTO_176_USB_ANALYSIS.md}.
 * <p>
 * <b>Uwaga eksploatacyjna:</b> programowanie <b>kasuje pamięć pomiarów</b>
 * (komenda {@code 0x32} na bankach 3 i 4). Most odmawia zapisu, gdy urządzenie
 * zgłasza tryb 6 — zakończoną misję z niepobranymi danymi — chyba że wywołanie
 * jawnie tego zażąda przez {@code force}.
 * <p>
 * <b>Ograniczenie:</b> typ termopary nie jest przekazywany do urządzenia; do
 * bloku konfiguracji trafia wyłącznie zakres pomiarowy kanału. Typ czujnika musi
 * prowadzić kartoteka rejestratora, powiązana ze świadectwem wzorcowania kanału.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Testo176ProgrammingService {

    /** Kasowanie trzech banków, zapis i weryfikacja round-trip trwają zauważalnie. */
    private static final Duration BRIDGE_TIMEOUT = Duration.ofSeconds(60);

    private static final DateTimeFormatter START_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PythonBridgeRunner bridgeRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- DTO ---------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProgrammingResult {
        public String status;
        public String message;
        public DeviceIdentity device;
        public SessionInfo session;
        public List<String> warnings = new ArrayList<>();

        public boolean isSuccess() {
            return "SUCCESS".equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceIdentity {
        public String articleNumber;
        public String model;
        public Integer manufacturingYear;
        public String serialNumber;
        public String firmwareVersion;
        public String hardwareVersion;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionInfo {
        public String programmingTimeUtc;
        public String firstMeasurementTimeUtc;
        public String firstMeasurementTimeLocal;
        public Integer startDelaySeconds;
        public Integer intervalMinutes;
        public Integer measurementsCount;
        public Integer channelMask;
        public List<ChannelInfo> channels = new ArrayList<>();
        public List<String> channelNames = new ArrayList<>();
        public String operator;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChannelInfo {
        public Integer channel;
        public Boolean enabled;
        public Double rangeUpperC;
        public Double rangeLowerC;
    }

    // --- API ---------------------------------------------------------------

    /**
     * Programuje rejestrator Testo 176 T4.
     *
     * @param intervalMinutes interwał pomiarów [min]
     * @param count           liczba odczytów
     * @param channels        numery aktywnych kanałów (1..4), co najmniej jeden
     * @param startLocalTime  czas pierwszego pomiaru w czasie lokalnym hosta
     * @param rangeUpperC     górna granica zakresu kanału [°C]
     * @param rangeLowerC     dolna granica zakresu kanału [°C]
     * @param channelNames    opisy kanałów w kolejności 1..4 — docelowo numery
     *                        świadectw wzorcowania z {@code Calibration}; brak
     *                        wartości daje znaki zapytania w raportach ComSoftu
     * @param operator        nazwa operatora zapisywana w metadanych urządzenia
     * @param force           {@code true} pozwala nadpisać niepobrane dane misji
     * @return wynik programowania; przy niepowodzeniu {@code status = "ERROR"}
     *         i wypełnione {@code message}
     */
    public ProgrammingResult programLogger(int intervalMinutes,
                                           int count,
                                           List<Integer> channels,
                                           LocalDateTime startLocalTime,
                                           double rangeUpperC,
                                           double rangeLowerC,
                                           List<String> channelNames,
                                           String operator,
                                           boolean force) {

        log.info("Programowanie Testo 176 T4: interwał={} min, odczytów={}, kanały={}, "
                        + "start={}, zakres={}…{} °C, force={}",
                intervalMinutes, count, channels, startLocalTime,
                rangeLowerC, rangeUpperC, force);

        ProgrammingResult result = new ProgrammingResult();

        try {
            File script = getOrExtractResource(
                    "testo/testo_176_programmer.py", "testo_176_programmer.py");
            if (script == null || !script.exists()) {
                result.status = "ERROR";
                result.message = "Nie znaleziono skryptu testo_176_programmer.py w zasobach.";
                return result;
            }

            if (channels == null || channels.isEmpty()) {
                result.status = "ERROR";
                result.message = "Trzeba wskazać przynajmniej jeden kanał pomiarowy.";
                return result;
            }

            String channelSpec = channels.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            List<String> command = new ArrayList<>(List.of(
                    "--interval-minutes", String.valueOf(intervalMinutes),
                    "--count", String.valueOf(count),
                    "--channels", channelSpec,
                    "--start", startLocalTime.format(START_FORMAT),
                    "--range-upper", String.valueOf(rangeUpperC),
                    "--range-lower", String.valueOf(rangeLowerC)));

            if (channelNames != null && channelNames.stream().anyMatch(
                    n -> n != null && !n.isBlank())) {
                command.add("--channel-names");
                command.add(channelNames.stream()
                        .map(n -> n == null ? "" : n.replace(",", " "))
                        .collect(Collectors.joining(",")));
            }
            if (operator != null && !operator.isBlank()) {
                command.add("--operator");
                command.add(operator);
            }
            if (force) {
                command.add("--force");
            }

            PythonBridgeRunner.BridgeResult bridge =
                    bridgeRunner.run(script, command, BRIDGE_TIMEOUT);

            if (bridge.timedOut()) {
                result.status = "ERROR";
                result.message = "Przekroczono czas oczekiwania ("
                        + BRIDGE_TIMEOUT.toSeconds() + " s) na programowanie Testo 176 T4. "
                        + "Stan urządzenia jest nieokreślony — zweryfikuj konfigurację "
                        + "przed użyciem rejestratora.";
                return result;
            }

            if (bridge.stdout().isEmpty()) {
                result.status = "ERROR";
                result.message = "Most Python nie zwrócił żadnych danych na stdout.";
                return result;
            }

            try {
                ProgrammingResult parsed =
                        objectMapper.readValue(bridge.stdout(), ProgrammingResult.class);
                if (parsed.isSuccess()) {
                    log.info("Testo 176 T4 (S/N {}) zaprogramowany i zweryfikowany.",
                            parsed.device != null ? parsed.device.serialNumber : "?");
                    parsed.warnings.forEach(w -> log.warn("Most 176 T4: {}", w));
                } else {
                    log.error("Programowanie Testo 176 T4 nieudane: {}", parsed.message);
                }
                return parsed;
            } catch (Exception parseException) {
                log.error("Nie udało się sparsować odpowiedzi mostu 176 T4. Wyjście:\n{}",
                        bridge.stdout(), parseException);
                result.status = "ERROR";
                result.message = "Błąd parsowania odpowiedzi JSON mostu: "
                        + parseException.getMessage();
                return result;
            }

        } catch (Exception e) {
            log.error("Krytyczny błąd podczas programowania Testo 176 T4", e);
            result.status = "ERROR";
            result.message = "Krytyczny błąd wywołania mostu: " + e.getMessage();
            return result;
        }
    }

    /**
     * Lokalizuje skrypt w drzewie projektu (tryb developerski) albo wypakowuje go
     * z classpath do katalogu tymczasowego (tryb spakowany w JAR).
     */
    private File getOrExtractResource(String classpathPath, String targetFileName) {
        try {
            File localFile = new File("src/main/resources/" + classpathPath);
            if (localFile.exists()) {
                return localFile;
            }

            File dir = new File(System.getProperty("java.io.tmpdir")
                    + File.separator + "vcc_testo_176_programmer");
            if (!dir.exists() && !dir.mkdirs()) {
                log.error("Nie udało się utworzyć katalogu tymczasowego: {}", dir);
                return null;
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
