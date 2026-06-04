package com.mac.bry.desktop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lekki loader plików .env wczytujący zmienne środowiskowe jako właściwości systemowe Java
 * przed uruchomieniem kontekstu Spring Boot (zgodność z GxP i zasadą niemodyfikowania pom.xml).
 */
public class DotenvLoader {

    public static void load() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    // Usuń cudzysłowy jeśli występują
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                    // Nadpisz właściwości systemowe tylko gdy nie są jeszcze zdefiniowane w systemie operacyjnym
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, val);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Błąd ładowania pliku .env: " + e.getMessage());
        }
    }
}
