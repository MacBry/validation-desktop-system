package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.fail;

/**
 * Parsowanie widoków rejestratora bez kontekstu Springa. Łapie literówki w FXML
 * i w {@code %kluczach} — jedno i drugie wywala FXMLLoader dopiero w runtime,
 * czyli po kliknięciu przez operatora.
 */
@ExtendWith(JavaFxToolkitExtension.class)
class ThermoRecorderFxmlTest {

    private void assertLoads(String resource) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(resource), I18n.getBundle());
            // Atrapa zamiast null: widoki mają onAction="#...", więc FXMLLoader
            // wymaga kontrolera, a prawdziwy potrzebowałby kontekstu Springa.
            loader.setControllerFactory(Mockito::mock);
            loader.load();
        } catch (Exception e) {
            fail("Nie udało się załadować " + resource + ": " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("Karta szczegółów rejestratora parsuje się poprawnie")
    void shouldLoadDetailsView() {
        assertLoads("/ui/thermo_recorder_details.fxml");
    }

    @Test
    @DisplayName("Dialog rejestratora parsuje się po dodaniu pól dat W4")
    void shouldLoadRecorderDialog() {
        assertLoads("/ui/thermo_recorder_dialog.fxml");
    }

    @Test
    @DisplayName("Lista rejestratorów parsuje się poprawnie")
    void shouldLoadRecordersView() {
        assertLoads("/ui/thermo_recorders.fxml");
    }
}