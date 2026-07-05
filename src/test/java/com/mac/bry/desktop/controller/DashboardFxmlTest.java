package com.mac.bry.desktop.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.fail;

class DashboardFxmlTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        // Inicjalizacja toolkitu JavaFX
        Platform.startup(latch::countDown);
        latch.await();
    }

    @AfterAll
    static void shutdownJavaFX() {
        // Bez tego wątek JavaFX (non-daemon) blokuje zamknięcie forka Surefire,
        // co na Linuksie kończy się błędem "The forked VM terminated"
        Platform.exit();
    }

    @Test
    void testLoadDashboardFxml() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/dashboard.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            // Mockowanie fabryki kontrolerów, aby nie potrzebować kontekstu Springa do samego testu parsowania FXML
            loader.setControllerFactory(param -> {
                try {
                    // Zwracamy pusty obiekt mock / null / lub pusty kontroler
                    return null; 
                } catch (Exception e) {
                    return null;
                }
            });
            loader.load();
            System.out.println("FXML loaded successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to load dashboard.fxml: " + e.getMessage(), e);
        }
    }
}
