package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.repository.ProcedureClassConfigRepository;
import com.mac.bry.desktop.repository.ThermoRecorderModelRepository;
import com.mac.bry.desktop.repository.UserVacationRepository;
import com.mac.bry.desktop.service.planner.RevalidationSchedulerEngine;
import com.mac.bry.desktop.service.planner.TestoDelayCalculatorService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Kontrakt między widokami planera a ich kontrolerami.
 * <p>
 * Test ładuje FXML z <b>prawdziwymi</b> kontrolerami zbudowanymi na
 * zamockowanych zależnościach, a nie z {@code null} jak
 * {@link DashboardFxmlTest}. Dzięki temu wychwytuje literówkę w {@code fx:id},
 * brakującą metodę {@code onAction} i brakujący klucz i18n — czyli błędy, które
 * przy samym parsowaniu FXML przechodzą, a wywracają aplikację dopiero
 * w rękach użytkownika.
 */
@ExtendWith(JavaFxToolkitExtension.class)
class PlannerFxmlTest {

    @BeforeAll
    static void initI18n() {
        I18n.init("pl");
    }

    @Test
    @DisplayName("planner_calendar.fxml ładuje się z kontrolerem kalendarza")
    void plannerCalendarLoads() throws Exception {
        loadOnFxThread("/ui/planner_calendar.fxml", type -> new PlannerCalendarController(
                mock(PlannedValidationTaskRepository.class),
                mock(RevalidationSchedulerEngine.class)));
    }

    @Test
    @DisplayName("procedure_class_config.fxml ładuje się z konfiguratorem klas procedur")
    void procedureClassConfigLoads() throws Exception {
        loadOnFxThread("/ui/procedure_class_config.fxml", type -> new ProcedureClassConfigController(
                mock(ProcedureClassConfigRepository.class),
                new TestoDelayCalculatorService()));
    }

    @Test
    @DisplayName("operator_vacation_dialog.fxml ładuje się z dialogiem nieobecności")
    void operatorVacationDialogLoads() throws Exception {
        loadOnFxThread("/ui/operator_vacation_dialog.fxml", type -> new OperatorVacationDialogController(
                mock(UserVacationRepository.class),
                mock(ApplicationEventPublisher.class)));
    }

    @Test
    @DisplayName("thermo_recorder_model_dialog.fxml ładuje się z polami kartoteki sprzętowej W4")
    void thermoRecorderModelDialogLoads() throws Exception {
        // Kartoteka modelu jest jedynym miejscem, w którym da się wprowadzić dane
        // katalogowe producenta — bez nich reguła W4 blokuje planowanie.
        loadOnFxThread("/ui/thermo_recorder_model_dialog.fxml",
                type -> new ThermoRecorderModelDialogController(mock(ThermoRecorderModelRepository.class)));
    }

    @Test
    @DisplayName("Klucze i18n kartoteki sprzętowej W4 istnieją w obu wersjach językowych")
    void hardwareSpecificationKeysResolveInBothLocales() {
        String[] keys = {
                "thermorecordermodeldialog.dane_katalogowe_producenta",
                "thermorecordermodeldialog.dane_katalogowe_opis",
                "thermorecordermodeldialog.pojemnosc_pamieci",
                "thermorecordermodeldialog.zakres_pracy_min",
                "thermorecordermodeldialog.zakres_pracy_max",
                "thermorecordermodeldialog.bateria_wymienna",
                "thermorecordermodeldialog.zywotnosc_baterii_dni",
                "thermorecordermodeldialog.cykl_referencyjny_min",
                "thermorecordermodeldialog.temperatura_referencyjna",
                "thermorecordermodeldialog.limit_pracy_dni"
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
    @DisplayName("main.fxml zawiera sekcję nawigacji planera")
    void mainNavigationExposesPlannerSection() {
        assertThat(I18n.t("main.nav.section.planner")).isEqualTo("PLANER REWALIDACJI");
        assertThat(I18n.t("main.nav.plannerCalendar")).contains("Kalendarz Planera");
        assertThat(I18n.t("main.nav.procedureClasses")).contains("Klasy Procedur");
        assertThat(I18n.t("main.nav.operatorVacations")).contains("Nieobecności Operatora");
    }

    @Test
    @DisplayName("Klucze i18n planera istnieją w obu wersjach językowych")
    void plannerKeysResolveInBothLocales() {
        String[] keys = {
                "planner.calendar.title", "planner.calendar.summary", "planner.calendar.col_resource",
                "planner.procedure.title", "planner.procedure.derived",
                "planner.vacation.title", "planner.vacation.hint_l4"
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