package com.mac.bry.desktop.controller;

import atlantafx.base.theme.Styles;
import com.mac.bry.desktop.service.Testo184ProgrammingService;
import com.mac.bry.desktop.service.TestoProgrammingService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.mac.bry.desktop.security.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestoProgrammingDialogController {

    private final TestoProgrammingService programmingService;
    private final Testo184ProgrammingService programming184Service;
    private final AuditService auditService;

    @FXML private ComboBox<String> deviceModelSelector;
    @FXML private ComboBox<String> driveLetterSelector;
    @FXML private Label driveLabel;
    @FXML private RowConstraints driveRowConstraint;

    @FXML private ComboBox<String> startModeSelector;
    @FXML private Label startModeLabel;
    @FXML private RowConstraints startModeRowConstraint;

    @FXML private Spinner<Integer> startDelaySpinner;
    @FXML private Label startDelayLabel;
    @FXML private RowConstraints startDelayRowConstraint;

    @FXML private TextField commentField;
    @FXML private Label commentLabel;
    @FXML private RowConstraints commentRowConstraint;

    @FXML private DatePicker startDatePicker;
    @FXML private TextField timeField;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Spinner<Integer> countSpinner;
    @FXML private Spinner<Double> upperLimitSpinner;
    @FXML private Spinner<Double> lowerLimitSpinner;
    
    @FXML private VBox messageContainer;
    @FXML private Label messageLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private Button programButton;

    @FXML
    public void initialize() {
        // Konfiguracja wyboru modelu rejestratora
        deviceModelSelector.setItems(FXCollections.observableArrayList("Testo 174T", "Testo 184T"));
        deviceModelSelector.setValue("Testo 174T");

        // Konfiguracja widoczności wiersza dysku USB
        driveLabel.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        driveLabel.managedProperty().bind(driveLabel.visibleProperty());
        driveLetterSelector.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        driveLetterSelector.managedProperty().bind(driveLetterSelector.visibleProperty());

        // Konfiguracja widoczności nowych wierszy parametrów 184T
        startModeLabel.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        startModeLabel.managedProperty().bind(startModeLabel.visibleProperty());
        startModeSelector.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        startModeSelector.managedProperty().bind(startModeSelector.visibleProperty());

        startDelayLabel.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        startDelayLabel.managedProperty().bind(startDelayLabel.visibleProperty());
        startDelaySpinner.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        startDelaySpinner.managedProperty().bind(startDelaySpinner.visibleProperty());

        commentLabel.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        commentLabel.managedProperty().bind(commentLabel.visibleProperty());
        commentField.visibleProperty().bind(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"));
        commentField.managedProperty().bind(commentField.visibleProperty());

        // Populating startModeSelector
        startModeSelector.setItems(FXCollections.observableArrayList("Czasowy (automatyczny)", "Ręczny (przycisk)"));
        startModeSelector.setValue("Czasowy (automatyczny)");

        // Wyłączanie daty/czasu startu, gdy wybrany jest start ręczny (przycisk) dla Testo 184T
        startDatePicker.disableProperty().bind(
                startModeSelector.valueProperty().isEqualTo("Ręczny (przycisk)")
                .and(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"))
        );
        timeField.disableProperty().bind(
                startModeSelector.valueProperty().isEqualTo("Ręczny (przycisk)")
                .and(deviceModelSelector.valueProperty().isEqualTo("Testo 184T"))
        );

        // Dynamiczne ukrywanie/pokazywanie wierszy w GridPane
        deviceModelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean is184 = "Testo 184T".equals(newVal);
            toggleRow(driveRowConstraint, is184, 30);
            toggleRow(startModeRowConstraint, is184, 30);
            toggleRow(startDelayRowConstraint, is184, 30);
            toggleRow(commentRowConstraint, is184, 30);
            
            if (is184) {
                scanDrives();
                // Testo 184T - min interwał 1 minuta
                intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440, 15, 5));
            } else {
                intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440, 15, 5));
            }

            if (deviceModelSelector.getScene() != null && deviceModelSelector.getScene().getWindow() instanceof Stage stage) {
                stage.setTitle("Programowanie Rejestratora " + newVal);
            }
            requestStageResize();
        });

        // Domyślnie wiersze są ukryte (T174)
        toggleRow(driveRowConstraint, false, 30);
        toggleRow(startModeRowConstraint, false, 30);
        toggleRow(startDelayRowConstraint, false, 30);
        toggleRow(commentRowConstraint, false, 30);

        // Domyślne wartości: Start za 2h
        LocalDateTime defaultStart = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
        startDatePicker.setValue(defaultStart.toLocalDate());
        timeField.setText(defaultStart.toLocalTime().toString());

        // Interwał (domyślnie 15 min, krok co 5 min)
        SpinnerValueFactory<Integer> intervalFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440, 15, 5);
        intervalSpinner.setValueFactory(intervalFactory);

        // Liczba pomiarów (domyślnie 40)
        SpinnerValueFactory<Integer> countFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 40, 10);
        countSpinner.setValueFactory(countFactory);

        // Start Delay (domyślnie 0, krok co 5 min)
        SpinnerValueFactory<Integer> startDelayFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1440, 0, 5);
        startDelaySpinner.setValueFactory(startDelayFactory);

        // Limity temperatury (domyślnie lodówka na krew 2-8°C)
        SpinnerValueFactory<Double> upperFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(-50.0, 150.0, 8.0, 0.5);
        upperLimitSpinner.setValueFactory(upperFactory);

        SpinnerValueFactory<Double> lowerFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(-50.0, 150.0, 2.0, 0.5);
        lowerLimitSpinner.setValueFactory(lowerFactory);
    }

    private void scanDrives() {
        List<String> removableDrives = new ArrayList<>();
        for (File root : File.listRoots()) {
            if (root.canWrite()) {
                removableDrives.add(root.getAbsolutePath());
            }
        }
        if (removableDrives.isEmpty()) {
            removableDrives.add("E:\\"); // Domyślna ścieżka fallback
        }
        driveLetterSelector.setItems(FXCollections.observableArrayList(removableDrives));
        driveLetterSelector.setValue(removableDrives.get(0));
    }

    @FXML
    private void handleProgram(ActionEvent event) {
        // Zbieranie i walidacja danych
        String selectedModel = deviceModelSelector.getValue();
        String selectedDrive = driveLetterSelector.getValue();

        if ("Testo 184T".equals(selectedModel) && (selectedDrive == null || selectedDrive.isEmpty())) {
            showError("Wybierz dysk docelowy dla rejestratora Testo 184T.");
            return;
        }

        LocalDate date = startDatePicker.getValue();
        if (date == null) {
            showError("Wybierz datę rozpoczęcia.");
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(timeField.getText());
        } catch (DateTimeParseException e) {
            showError("Nieprawidłowy format czasu (oczekiwany HH:mm).");
            return;
        }

        LocalDateTime startLocalTime = LocalDateTime.of(date, time);
        boolean isManualStart184 = "Testo 184T".equals(selectedModel) && "Ręczny (przycisk)".equals(startModeSelector.getValue());
        if (!isManualStart184) {
            if (startLocalTime.isBefore(LocalDateTime.now())) {
                showError("Czas startu nie może być w przeszłości.");
                return;
            }
        } else {
            startLocalTime = LocalDateTime.now().plusMinutes(1);
        }

        int interval = intervalSpinner.getValue();
        int count = countSpinner.getValue();
        double upper = upperLimitSpinner.getValue();
        double lower = lowerLimitSpinner.getValue();

        if (lower >= upper) {
            showError("Dolny próg alarmu musi być niższy niż próg górny.");
            return;
        }

        // Zablokuj UI
        programButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setVisible(true);
        messageContainer.setVisible(false);
        messageContainer.setManaged(false);
        requestStageResize();
        statusLabel.setText("Programowanie, proszę czekać...");

        final String comment = commentField.getText() != null && !commentField.getText().trim().isEmpty()
                ? commentField.getText().trim()
                : "Programowanie przez aplikacje Validation Desktop";
        final int startMode = isManualStart184 ? 3 : 1;
        final int startDelay = startDelaySpinner.getValue() != null ? startDelaySpinner.getValue() : 0;
        final LocalDateTime finalStartLocalTime = startLocalTime;

        // Uruchomienie w tle, aby nie zamrozić UI
        Task<Boolean> programmingTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if ("Testo 184T".equals(selectedModel)) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = (auth != null) ? auth.getName() : "System";
                    return programming184Service.programLogger(
                            selectedDrive,
                            interval,
                            count,
                            finalStartLocalTime,
                            startMode,
                            startDelay,
                            upper,
                            60, // 60 minutes threshold
                            lower,
                            60, // 60 minutes threshold
                            username,
                            comment
                    );
                } else {
                    return programmingService.programTestoLogger(interval, count, finalStartLocalTime, upper, lower);
                }
            }
        };

        programmingTask.setOnSucceeded(e -> {
            boolean success = programmingTask.getValue();
            if (success) {
                showSuccess("Loger został pomyślnie zaprogramowany!\nMożesz go umieścić w urządzeniu chłodniczym.");
                // Zmiana tekstu na przycisku i akcji na wyjście
                programButton.setText("Gotowe");
                programButton.setOnAction(this::handleCancel);
                programButton.getStyleClass().remove(Styles.ACCENT);
                programButton.getStyleClass().add(Styles.SUCCESS);

                // Zapis do Audit Trail
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String username = (auth != null) ? auth.getName() : "System";
                String details;
                if ("Testo 184T".equals(selectedModel)) {
                    details = String.format("Programowanie Testo 184T: dysk=%s, interwał=%d min, pomiarów=%d, start=%s, tryb=%d, opóźnienie=%d min, limity=[%.1f°C, %.1f°C]",
                            selectedDrive, interval, count, finalStartLocalTime.toString(), startMode, startDelay, lower, upper);
                } else {
                    details = String.format("Programowanie USB Testo 174T: interwał=%d min, pomiarów=%d, start=%s, limity=[%.1f°C, %.1f°C]",
                            interval, count, finalStartLocalTime.toString(), lower, upper);
                }
                auditService.logAccessEvent(username, "USB_PROGRAMMING", details);
                log.info("Zapisano log audytowy programowania GxP dla: {}", username);

            } else {
                if ("Testo 184T".equals(selectedModel)) {
                    showError("Błąd zapisu pliku XML. Upewnij się, że dysk rejestratora jest dostępny do zapisu i ponów próbę.");
                } else {
                    showError("Błąd programowania. Upewnij się, że loger jest w kołysce USB i ponów próbę.");
                }
            }
            unlockUI();
        });

        programmingTask.setOnFailed(e -> {
            Throwable ex = programmingTask.getException();
            log.error("Wystąpił wyjątek podczas wykonywania Taska", ex);
            showError("Wystąpił błąd komunikacji: " + ex.getMessage());
            unlockUI();
        });

        new Thread(programmingTask).start();
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        Stage stage = (Stage) startDatePicker.getScene().getWindow();
        stage.close();
    }

    private void unlockUI() {
        programButton.setDisable(false);
        progressIndicator.setVisible(false);
        statusLabel.setVisible(false);
    }

    private void showError(String message) {
        messageContainer.setVisible(true);
        messageContainer.setManaged(true);
        messageLabel.setText(message);
        messageContainer.getStyleClass().removeAll("success", "danger");
        messageContainer.getStyleClass().add(Styles.DANGER);
        messageLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        messageContainer.setStyle("-fx-background-color: -color-danger-emphasis; -fx-background-radius: 4; -fx-padding: 10;");
        requestStageResize();
    }

    private void showSuccess(String message) {
        messageContainer.setVisible(true);
        messageContainer.setManaged(true);
        messageLabel.setText(message);
        messageContainer.getStyleClass().removeAll("success", "danger");
        messageContainer.getStyleClass().add(Styles.SUCCESS);
        messageLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        messageContainer.setStyle("-fx-background-color: -color-success-emphasis; -fx-background-radius: 4; -fx-padding: 10;");
        requestStageResize();
    }

    private void toggleRow(RowConstraints constraint, boolean visible, double height) {
        if (visible) {
            constraint.setMaxHeight(Double.MAX_VALUE);
            constraint.setMinHeight(Region.USE_COMPUTED_SIZE);
            constraint.setPrefHeight(Region.USE_COMPUTED_SIZE);
        } else {
            constraint.setMaxHeight(0);
            constraint.setMinHeight(0);
            constraint.setPrefHeight(0);
        }
    }

    private void requestStageResize() {
        if (startDatePicker.getScene() != null && startDatePicker.getScene().getWindow() instanceof Stage stage) {
            Platform.runLater(stage::sizeToScene);
        }
    }
}
