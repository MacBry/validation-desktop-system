package com.mac.bry.desktop.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wspólny runner mostów Pythonowych (Testo 174T/184).
 * <p>
 * Rozwiązuje trzy problemy wcześniejszej implementacji per-serwis:
 * <ul>
 *   <li><b>Kodowanie:</b> stdout czytany jako bajty i dekodowany raz jako UTF-8
 *       (wcześniej platform-default charset + dekodowanie per 1024-bajtowy chunk
 *       mogło przecinać znaki wielobajtowe i psuć JSON). Proces dostaje
 *       {@code PYTHONIOENCODING=utf-8}, więc obie strony mówią tym samym kodowaniem.</li>
 *   <li><b>Zawieszenie:</b> stdout drenowany na osobnym wątku, a {@code waitFor(timeout)}
 *       działa równolegle — zawieszony proces jest ubijany po timeoucie
 *       (wcześniej blokujący read przed waitFor unieważniał timeout).</li>
 *   <li><b>Komenda python:</b> konfigurowalna przez {@code testo.python-command}
 *       (np. pełna ścieżka, gdy 'python' to stub Microsoft Store).</li>
 * </ul>
 */
@Component
@Slf4j
public class PythonBridgeRunner {

    private final String pythonCommand;

    public PythonBridgeRunner(@Value("${testo.python-command:python}") String pythonCommand) {
        this.pythonCommand = pythonCommand;
    }

    /** Wynik uruchomienia mostu. */
    public record BridgeResult(int exitCode, String stdout, boolean timedOut) {
        public boolean isSuccess() {
            return !timedOut && exitCode == 0;
        }
    }

    /**
     * Uruchamia skrypt Pythona z argumentami i czeka na zakończenie.
     *
     * @param script     plik skryptu (katalog skryptu staje się working dir)
     * @param scriptArgs argumenty przekazywane do skryptu
     * @param timeout    maksymalny czas działania procesu
     */
    public BridgeResult run(File script, List<String> scriptArgs, Duration timeout)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(script.getAbsolutePath());
        command.addAll(scriptArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(script.getParentFile());
        pb.redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        log.info("Uruchamianie mostu Python: {}", command);
        Process process = pb.start();

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        Thread drainThread = new Thread(() -> {
            try (InputStream is = process.getInputStream()) {
                is.transferTo(stdoutBytes);
            } catch (IOException e) {
                log.debug("Strumień stdout mostu Python zamknięty: {}", e.getMessage());
            }
        }, "python-bridge-stdout-drain");
        drainThread.setDaemon(true);
        drainThread.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            drainThread.join(2000);
            log.error("Most Python przekroczył limit czasu {} s — proces ubity. Komenda: {}",
                    timeout.toSeconds(), command);
            return new BridgeResult(-1, stdoutBytes.toString(StandardCharsets.UTF_8), true);
        }

        drainThread.join(5000);
        String stdout = stdoutBytes.toString(StandardCharsets.UTF_8).trim();
        return new BridgeResult(process.exitValue(), stdout, false);
    }
}
