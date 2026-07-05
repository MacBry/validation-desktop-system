package com.mac.bry.desktop.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy infrastruktury i18n — parzystość bundli i pokrycie kluczy FXML.
 * Chronią przed dwoma regresami przy konwersji kolejnych ekranów:
 * kluczem dodanym tylko w jednym języku oraz literówką w %kluczu w FXML
 * (która wywala FXMLLoader dopiero w runtime).
 */
class I18nTest {

    private static final Pattern FXML_KEY_PATTERN = Pattern.compile("\"%([a-zA-Z0-9._]+)\"");

    @AfterEach
    void resetLocale() {
        I18n.init("pl");
    }

    @Test
    @DisplayName("TC-I18N-001: Bundle PL i EN mają identyczne zestawy kluczy")
    void tc_i18n_001_bundleKeyParity() {
        ResourceBundle pl = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("pl"));
        ResourceBundle en = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("en"));

        Set<String> plKeys = new TreeSet<>(pl.keySet());
        Set<String> enKeys = new TreeSet<>(en.keySet());

        assertThat(enKeys)
                .as("Klucze obecne w PL, brakujące w EN")
                .containsAll(plKeys);
        assertThat(plKeys)
                .as("Klucze obecne w EN, brakujące w PL")
                .containsAll(enKeys);
    }

    @Test
    @DisplayName("TC-I18N-002: Każdy %klucz użyty w FXML istnieje w bundle bazowym")
    void tc_i18n_002_allFxmlKeysResolvable() throws IOException, URISyntaxException {
        ResourceBundle base = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("pl"));
        Path uiDir = Paths.get(getClass().getResource("/ui").toURI());

        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(uiDir)) {
            files.filter(p -> p.toString().endsWith(".fxml")).forEach(fxml -> {
                try {
                    Matcher m = FXML_KEY_PATTERN.matcher(Files.readString(fxml));
                    while (m.find()) {
                        String key = m.group(1);
                        if (!base.containsKey(key)) {
                            missing.add(fxml.getFileName() + " -> " + key);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertThat(missing)
                .as("Klucze %... z FXML bez tłumaczenia w messages.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("TC-I18N-003: t() tłumaczy per locale i formatuje parametry")
    void tc_i18n_003_translationPerLocale() {
        I18n.init("pl");
        assertThat(I18n.t("main.user.loggedInAs", "jkowalski"))
                .isEqualTo("Zalogowano jako: jkowalski");

        I18n.init("en");
        assertThat(I18n.t("main.user.loggedInAs", "jkowalski"))
                .isEqualTo("Logged in as: jkowalski");
    }

    @Test
    @DisplayName("TC-I18N-004: Brakujący klucz zwraca marker !klucz! zamiast wyjątku")
    void tc_i18n_004_missingKeyReturnsMarker() {
        assertThat(I18n.t("nonexistent.key")).isEqualTo("!nonexistent.key!");
    }

    @Test
    @DisplayName("TC-I18N-005: Nieznane locale spada na bundle bazowy (PL)")
    void tc_i18n_005_unknownLocaleFallsBackToBase() {
        I18n.init("de");
        assertThat(I18n.t("main.user.logout")).isEqualTo("Wyloguj");
    }
}
