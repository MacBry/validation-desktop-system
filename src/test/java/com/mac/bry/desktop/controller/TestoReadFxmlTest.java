package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.service.JavaFxChartRenderer;
import com.mac.bry.desktop.service.RecorderBatteryService;
import com.mac.bry.desktop.service.Testo184UsbImportService;
import com.mac.bry.desktop.service.TestoCsvExportService;
import com.mac.bry.desktop.service.TestoPdfReportService;
import com.mac.bry.desktop.service.TestoSimulationService;
import com.mac.bry.desktop.service.TestoUsbImportService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Kontrakt między widokiem odczytu Testo a jego kontrolerem.
 * <p>
 * Ekran ładowany jest z <b>prawdziwym</b> kontrolerem na zamockowanych
 * zależnościach — tak jak w {@link PlannerFxmlTest}, a nie z atrapą kontrolera.
 * Tylko wtedy test łapie to, co naprawdę psuje tę zakładkę: literówkę w
 * {@code fx:id}, brakującą metodę {@code onAction} i brakujący klucz i18n.
 * <p>
 * Test powstał, bo zakładka „Odczyt Testo” była jedną z częściej używanych,
 * a nie miała żadnego pokrycia: dołożenie {@link RecorderBatteryService} do
 * konstruktora kontrolera przechodziło przez kompilację i testy serwisów,
 * a błąd wyszedłby dopiero po kliknięciu przez operatora. Jest to o tyle
 * dotkliwe, że od migracji V36 właśnie ta zakładka jest ścieżką, którą
 * rejestrator odzyskuje stan baterii — bez niej planer blokuje każde badanie.
 */
@ExtendWith(JavaFxToolkitExtension.class)
class TestoReadFxmlTest {

    @BeforeAll
    static void initI18n() {
        I18n.init("pl");
    }

    @Test
    @DisplayName("testo_read.fxml ładuje się z kontrolerem odczytu")
    void testoReadViewLoads() throws Exception {
        loadOnFxThread("/ui/testo_read.fxml", type -> new TestoReadController(
                mock(TestoUsbImportService.class),
                mock(Testo184UsbImportService.class),
                mock(RecorderBatteryService.class),
                mock(TestoPdfReportService.class),
                mock(AuditService.class),
                mock(TestoSimulationService.class),
                mock(TestoCsvExportService.class),
                mock(JavaFxChartRenderer.class)));
    }

    @Test
    @DisplayName("Klucze i18n stanu baterii istnieją w obu wersjach językowych")
    void batteryKeysResolveInBothLocales() {
        // Komunikaty zapisu stanu baterii składane są w kodzie, więc %klucz z FXML
        // ich nie obejmuje — bez tej bramki literówka pokazałaby operatorowi "!klucz!"
        // dokładnie w miejscu, w którym ma się dowiedzieć, czy kartoteka została
        // zaktualizowana.
        String[] keys = {
                "testoread.stan_baterii",
                "testoread.battery.days",
                "testoread.battery.notAvailable",
                "testoread.battery.saved",
                "testoread.battery.notInRegistry"
        };

        for (String locale : new String[]{"pl", "en"}) {
            I18n.init(locale);
            for (String key : keys) {
                assertThat(I18n.t(key))
                        .as("klucz %s w locale %s", key, locale)
                        .doesNotStartWith("!");
            }
        }
        I18n.init("pl");
    }

    @Test
    @DisplayName("Etykieta stanu baterii nie obiecuje procentów — sprzęt podaje dni")
    void batteryLabelDoesNotPromisePercent() {
        // Regresja na jednostkę: do sierpnia 2026 pole nosiło etykietę „Stan baterii (%)"
        // i pokazywało młodszy bajt progu alarmowego temperatury.
        for (String locale : new String[]{"pl", "en"}) {
            I18n.init(locale);
            assertThat(I18n.t("testoread.stan_baterii"))
                    .as("etykieta w locale %s", locale)
                    .doesNotContain("%");
            assertThat(I18n.t("testoread.battery.days", 387))
                    .as("wartość w locale %s", locale)
                    .contains("387");
        }
        I18n.init("pl");
    }

    /**
     * FXMLLoader musi działać na wątku aplikacji JavaFX — kontrolery tworzą
     * kontrolki i rejestrują listenery.
     */
    private void loadOnFxThread(String fxmlPath, javafx.util.Callback<Class<?>, Object> controllerFactory)
            throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath), I18n.getBundle());
                loader.setControllerFactory(controllerFactory);
                loader.load();
            } catch (Exception e) {
                failure.set(e);
            } finally {
                latch.countDown();
            }
        });

        assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("ładowanie %s nie zakończyło się w 30 s", fxmlPath)
                .isTrue();

        if (failure.get() != null) {
            throw new AssertionError("Nie udało się załadować " + fxmlPath
                    + ": " + failure.get().getMessage(), failure.get());
        }
    }
}