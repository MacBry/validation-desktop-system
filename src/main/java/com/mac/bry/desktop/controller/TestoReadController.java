package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.service.TestoPdfReportService;
import com.mac.bry.desktop.service.TestoUsbImportService;
import com.mac.bry.desktop.service.TestoSimulationService;
import com.mac.bry.desktop.service.SimulationProfile;
import com.mac.bry.desktop.service.TestoCsvExportService;
import com.mac.bry.desktop.service.JavaFxChartRenderer;
import com.mac.bry.desktop.controller.helper.TestoReadTableHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import com.mac.bry.desktop.service.Testo184UsbImportService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestoReadController {

    private final TestoUsbImportService testoUsbImportService;
    private final Testo184UsbImportService testo184UsbImportService;
    private final TestoPdfReportService testoPdfReportService;
    private final com.mac.bry.desktop.security.service.AuditService auditService;
    private final TestoSimulationService testoSimulationService;
    private final TestoCsvExportService testoCsvExportService;
    private final JavaFxChartRenderer javaFxChartRenderer;

    @FXML private TextField modelField;
    @FXML private TextField serialNumberField;
    @FXML private TextField batteryLevelField;
    @FXML private Button readUsbButton;
    @FXML private Button importPdf184Button;
    @FXML private ProgressIndicator readProgressIndicator;
    @FXML private ProgressBar readProgressBar;

    @FXML private TextField intervalField;
    @FXML private TextField delayField;
    @FXML private TextField countField;
    @FXML private TextArea commentsArea;

    @FXML private TableView<ThermoMeasurementPoint> measurementsTable;
    @FXML private TableColumn<ThermoMeasurementPoint, Integer> colIndex;
    @FXML private TableColumn<ThermoMeasurementPoint, LocalDateTime> colTime;
    @FXML private TableColumn<ThermoMeasurementPoint, Double> colTemp;

    // Linie Wykresu
    @FXML private VBox chartContainer;
    @FXML private LineChart<Number, Number> temperatureChart;
    @FXML private NumberAxis xAxisTime;
    @FXML private NumberAxis yAxisTemp;

    @FXML private Label statusLabel;
    @FXML private Button clearButton;
    @FXML private Button reportPdfButton;
    @FXML private Button exportCsvButton;

    private final ObservableList<ThermoMeasurementPoint> pointsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        log.info("Inicjalizacja autonomicznego kontrolera Odczytu Testo GUI");

        TestoReadTableHelper.setupTableColumns(colIndex, colTime, colTemp);

        measurementsTable.setItems(pointsList);
    }

    private File findTesto184PdfOnRemovableDrives() {
        for (File root : File.listRoots()) {
            try {
                if (root.exists() && root.isDirectory()) {
                    File[] files = root.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && f.getName().toLowerCase().endsWith(".pdf")) {
                                String name = f.getName().toLowerCase();
                                if (name.contains("testo") && name.contains("184")) {
                                    return f;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Błąd podczas skanowania dysku: " + root.getAbsolutePath(), e);
            }
        }
        return null;
    }

    @FXML
    public void handleReadUSB(ActionEvent event) {
        log.info("Rozpoczęcie odczytu USB za pośrednictwem serwisu TestoUsbImportService");

        // Wizualne wskaźniki wczytywania
        readProgressIndicator.setVisible(true);
        readProgressIndicator.setManaged(true);
        readProgressBar.setVisible(true);
        readProgressBar.setManaged(true);
        readUsbButton.setDisable(true);
        importPdf184Button.setDisable(true);
        exportCsvButton.setDisable(true);
        reportPdfButton.setDisable(true);
        statusLabel.setText("Status: Wykrywanie podłączonych urządzeń Testo...");

        // Asynchroniczne zadanie w tle (odczyt USB przez most Python)
        Task<TestoUsbImportService.TestoImportResult> task = new Task<>() {
            @Override
            protected TestoUsbImportService.TestoImportResult call() throws Exception {
                // 1. Spróbuj wykryć podłączony rejestrator Testo 184 i jego raport PDF
                File testo184Pdf = findTesto184PdfOnRemovableDrives();
                if (testo184Pdf != null) {
                    log.info("Wykryto rejestrator Testo 184 na dysku. Importowanie pliku: {}", testo184Pdf.getAbsolutePath());
                    updateMessage("Status: Wykryto Testo 184. Importowanie raportu PDF...");
                    return testo184UsbImportService.importFromPdf(testo184Pdf);
                }

                // 2. Jeśli nie wykryto Testo 184, spróbuj odczytać Testo 174T przez FTDI
                updateMessage("Status: Inicjalizacja połączenia USB (T174T)...");
                return testoUsbImportService.readFromUsb();
            }
        };

        readProgressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(workerStateEvent -> {
            statusLabel.textProperty().unbind();
            TestoUsbImportService.TestoImportResult results = task.getValue();

            // Wyłączenie pasków wczytywania
            readProgressIndicator.setVisible(false);
            readProgressIndicator.setManaged(false);
            readProgressBar.setVisible(false);
            readProgressBar.setManaged(false);
            readProgressBar.progressProperty().unbind();
            readUsbButton.setDisable(false);
            importPdf184Button.setDisable(false);

            // Sprawdzenie błędów komunikacji (np. brak podłączonego sprzętu)
            if ("ERROR".equals(results.status)) {
                log.warn("Odczyt USB zakończony kodem ERROR: {}", results.message);
                
                // Eleganckie zapytanie o uruchomienie symulacji z braku podłączonego sprzętu
                Alert confirmSim = new Alert(Alert.AlertType.CONFIRMATION);
                confirmSim.setTitle("Brak połączenia USB");
                confirmSim.setHeaderText("Nie wykryto fizycznego rejestratora Testo 174T / 184T");
                confirmSim.setContentText("Szczegóły: " + results.message + "\n\n"
                        + "Czy chcesz uruchomić tryb symulacji metrologicznej komory chłodniczej w celach demonstracyjnych?");
                
                ButtonType btnSim = new ButtonType("Uruchom Symulację");
                ButtonType btnCancel = new ButtonType("Anuluj", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirmSim.getButtonTypes().setAll(btnSim, btnCancel);
                
                var choice = confirmSim.showAndWait();
                if (choice.isPresent() && choice.get() == btnSim) {
                    runSimulation();
                } else {
                    statusLabel.setText("Status: Odczyt anulowany (Brak fizycznego połączenia USB).");
                }
                return;
            }

            // Pomyślny odczyt z USB! Dekodowanie wyników
            pointsList.clear();
            for (TestoUsbImportService.MeasurementPointDto pt : results.measurements) {
                String cleanTime = pt.timestampLocal.length() > 19 ? pt.timestampLocal.substring(0, 19) : pt.timestampLocal;
                pointsList.add(ThermoMeasurementPoint.builder()
                        .measurementIndex(pt.index)
                        .timestampLocal(LocalDateTime.parse(cleanTime))
                        .rawCelsius(pt.valueCelsius)
                        .build());
            }

            // Dane urządzenia
            modelField.setText(results.device.model);
            serialNumberField.setText(results.device.serialNumber);
            batteryLevelField.setText(results.session.batteryLevelPercent >= 0 ? results.session.batteryLevelPercent + "%" : "N/D");
            
            // Metadane sesji
            intervalField.setText(results.session.intervalMinutes + " minut");
            delayField.setText(results.session.startDelayMinutes > 0 ? results.session.startDelayMinutes + " minut" : "Brak opóźnienia");
            countField.setText(String.valueOf(pointsList.size()));

            // Renderowanie interaktywnego wykresu JavaFX
            updateChart();

            exportCsvButton.setDisable(false);
            reportPdfButton.setDisable(false);
            statusLabel.setText("Status: Pomyślnie odczytano z portu USB: " + pointsList.size() + " punktów pomiarowych.");
            log.info("Pomyślnie wczytano dane z rzeczywistego USB Testo.");

            // Zapis do Audit Trail (Historia Odczytów)
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null) ? auth.getName() : "System";
            String details = String.format("Pobrano dane USB z %s (SN: %s). Ilość pomiarów: %d.", 
                    results.device.model, results.device.serialNumber, pointsList.size());
            auditService.logAccessEvent(username, "USB_READING", details);
        });

        task.setOnFailed(workerStateEvent -> {
            statusLabel.textProperty().unbind();
            Throwable e = task.getException();
            log.error("Wyjątek podczas odczytu USB", e);

            readProgressIndicator.setVisible(false);
            readProgressIndicator.setManaged(false);
            readProgressBar.setVisible(false);
            readProgressBar.setManaged(false);
            readProgressBar.progressProperty().unbind();
            readUsbButton.setDisable(false);
            importPdf184Button.setDisable(false);

            statusLabel.setText("Status: Krytyczny błąd interfejsu Java.");
            showErrorAlert("Krytyczny błąd", "Wystąpił błąd wewnątrz maszyny JVM", e != null ? e.getMessage() : "Brak szczegółów");
        });

        new Thread(task).start();
    }

    private void runSimulation() {
        List<SimulationProfile> profiles = List.of(
                SimulationProfile.STABLE,
                SimulationProfile.DRIFT,
                SimulationProfile.SPIKES,
                SimulationProfile.DRIFT_AND_SPIKES
        );
        ChoiceDialog<SimulationProfile> choiceDialog = new ChoiceDialog<>(SimulationProfile.STABLE, profiles);
        choiceDialog.setTitle("Profil Symulacji");
        choiceDialog.setHeaderText("Wybierz profil symulacji do załadowania");
        choiceDialog.setContentText("Profil:");

        choiceDialog.showAndWait().ifPresent(profile -> {
            pointsList.clear();
            List<ThermoMeasurementPoint> simPoints = testoSimulationService.generateSimulationPoints(
                    200, 10, profile, 4.8, 1, LocalDateTime.now().minusHours(33));
            pointsList.addAll(simPoints);

            modelField.setText("Testo 174T (Symulacja: " + profile.getDisplayName() + ")");
            serialNumberField.setText("SN-174-20485912-SIM");
            batteryLevelField.setText("98%");
            intervalField.setText("10 minut");
            delayField.setText("Brak opóźnienia");
            countField.setText(String.valueOf(pointsList.size()));

            // Renderowanie interaktywnego wykresu JavaFX
            updateChart();

            exportCsvButton.setDisable(false);
            reportPdfButton.setDisable(false);
            statusLabel.setText("Status: Uruchomiono symulację: " + profile.getDisplayName());
            log.info("Uruchomiono symulację: " + profile.getDisplayName() + " (" + pointsList.size() + " punktów).");
        });
    }

    private void updateChart() {
        if (pointsList.isEmpty()) {
            chartContainer.setVisible(false);
            chartContainer.setManaged(false);
            return;
        }

        // Konfiguracja osi X jako osi liczbowej ze StringConverterem czasowym
        xAxisTime.setAutoRanging(true);
        xAxisTime.setForceZeroInRange(false);
        xAxisTime.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                int idx = object.intValue();
                if (idx >= 1 && idx <= pointsList.size()) {
                    return pointsList.get(idx - 1).getTimestampLocal().format(DateTimeFormatter.ofPattern("HH:mm"));
                }
                return "";
            }
            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Temperatura (°C)");

        DateTimeFormatter tooltipTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < pointsList.size(); i++) {
            ThermoMeasurementPoint pt = pointsList.get(i);
            XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(i + 1, pt.getRawCelsius());
            
            // Konfiguracja interaktywnego podglądu (hover tooltip & zoom effect) po utworzeniu Node przez JavaFX
            dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    String timeStr = pt.getTimestampLocal().format(tooltipTimeFormatter);
                    Tooltip tooltip = new Tooltip(String.format(
                            "Indeks: %d\nCzas: %s\nTemperatura: %.1f °C", 
                            pt.getMeasurementIndex(), timeStr, pt.getRawCelsius()
                    ));
                    tooltip.setShowDelay(javafx.util.Duration.millis(50));
                    tooltip.setHideDelay(javafx.util.Duration.millis(50));
                    tooltip.setShowDuration(javafx.util.Duration.INDEFINITE);
                    tooltip.setStyle(
                        "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: rgba(30, 41, 59, 0.9); " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 8px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 5, 0.0, 0, 2);"
                    );
                    Tooltip.install(newNode, tooltip);

                    // Efekt mikro-animacji po najechaniu kursorem (powiększenie i zmiana kursora)
                    newNode.setOnMouseEntered(e -> {
                        newNode.setScaleX(1.8);
                        newNode.setScaleY(1.8);
                        newNode.setCursor(javafx.scene.Cursor.HAND);
                        newNode.setStyle("-fx-background-color: -color-accent-emphasis;");
                    });
                    newNode.setOnMouseExited(e -> {
                        newNode.setScaleX(1.0);
                        newNode.setScaleY(1.0);
                        newNode.setCursor(javafx.scene.Cursor.DEFAULT);
                        newNode.setStyle("");
                    });
                }
            });
            
            series.getData().add(dataPoint);
        }

        temperatureChart.getData().clear();
        temperatureChart.getData().add(series);

        // Odsłoń kontener z wykresem nad tabelą
        chartContainer.setVisible(true);
        chartContainer.setManaged(true);
    }

    @FXML
    public void handleExportCSV(ActionEvent event) {
        log.info("Rozpoczęcie eksportu danych pomiarowych do pliku CSV");

        if (pointsList.isEmpty()) {
            showWarningAlert("Brak danych", "Tabela pomiarów jest pusta. Dokonaj najpierw odczytu USB.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zapisz dane odczytu Testo do CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pliki CSV (*.csv)", "*.csv"));
        
        String defaultFileName = "testo_174T_" + serialNumberField.getText() + "_" 
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".csv";
        fileChooser.setInitialFileName(defaultFileName);

        Stage stage = (Stage) readUsbButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try {
                testoCsvExportService.exportToCsv(
                        file,
                        modelField.getText(),
                        serialNumberField.getText(),
                        batteryLevelField.getText(),
                        intervalField.getText(),
                        pointsList.size(),
                        commentsArea.getText(),
                        pointsList
                );

                log.info("Dane pomyślnie wyeksportowane do: {}", file.getAbsolutePath());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Eksport zakończony");
                alert.setHeaderText("Eksport do pliku CSV zakończony sukcesem");
                alert.setContentText("Dane zostały zapisane do pliku:\n" + file.getAbsolutePath());
                alert.showAndWait();

            } catch (IOException e) {
                log.error("Błąd podczas zapisu pliku CSV", e);
                showErrorAlert("Błąd zapisu", "Nie udało się zapisać pliku CSV", e.getMessage());
            }
        }
    }

    @FXML
    public void handleGeneratePDF(ActionEvent event) {
        log.info("Generowanie rzeczywistego raportu pomiarowego PDF");

        if (pointsList.isEmpty()) {
            showWarningAlert("Brak danych", "Tabela pomiarów jest pusta. Dokonaj najpierw odczytu USB.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zapisz Raport Metrologiczny PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dokumenty PDF (*.pdf)", "*.pdf"));
        
        String defaultFileName = "raport_testo_" + serialNumberField.getText() + "_" 
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".pdf";
        fileChooser.setInitialFileName(defaultFileName);

        Stage stage = (Stage) readUsbButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file == null) {
            log.info("Anulowano generowanie raportu PDF przez użytkownika.");
            return;
        }

        File tempImageFile = null;
        try {
            statusLabel.setText("Status: Generowanie zrzutu wykresu o optymalnych proporcjach...");
            
            // Generowanie zrzutu wykresu przy użyciu JavaFxChartRenderer
            tempImageFile = javaFxChartRenderer.renderSeriesToPng(pointsList);
            
            statusLabel.setText("Status: Budowanie nienaruszalnego dokumentu PDF...");

            // Przygotowanie danych wejściowych do raportu
            TestoPdfReportService.TestoReportData reportData = new TestoPdfReportService.TestoReportData();
            reportData.model = modelField.getText();
            reportData.serialNumber = serialNumberField.getText();
            reportData.batteryLevel = batteryLevelField.getText();
            reportData.interval = intervalField.getText();
            reportData.startDelay = delayField.getText();
            reportData.comments = commentsArea.getText();
            reportData.measurements = new ArrayList<>(pointsList);

            // Wywołanie serwisu generowania raportu PDF
            testoPdfReportService.generatePdfReport(reportData, file, tempImageFile);

            statusLabel.setText("Status: Raport PDF pomyślnie zapisany!");
            log.info("Pomyślnie wygenerowano raport GxP PDF: {}", file.getAbsolutePath());

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Raport PDF Wygenerowany");
            successAlert.setHeaderText("Generowanie raportu PDF zakończone powodzeniem");
            successAlert.setContentText("Nienaruszalny raport metrologiczny został pomyślnie zapisany do pliku:\n" + file.getAbsolutePath());
            successAlert.showAndWait();

        } catch (Exception e) {
            log.error("Krytyczny błąd podczas generowania raportu PDF z serii pomiarowej", e);
            statusLabel.setText("Status: Błąd zapisu raportu PDF!");
            showErrorAlert("Błąd zapisu PDF", "Nie udało się wygenerować raportu PDF z serii", e.getMessage());
        } finally {
            // Posprzątanie tymczasowego zrzutu wykresu
            if (tempImageFile != null && tempImageFile.exists()) {
                tempImageFile.delete();
            }
        }
    }

    @FXML
    public void handleImportPdf184(ActionEvent event) {
        log.info("Rozpoczęcie importu pliku PDF Testo 184");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Wybierz plik raportu PDF Testo 184");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pliki PDF (*.pdf)", "*.pdf"));

        Stage stage = (Stage) readUsbButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file == null) {
            log.info("Anulowano wybór pliku PDF przez użytkownika.");
            return;
        }

        // Wizualne wskaźniki wczytywania
        readProgressIndicator.setVisible(true);
        readProgressIndicator.setManaged(true);
        readProgressBar.setVisible(true);
        readProgressBar.setManaged(true);
        readUsbButton.setDisable(true);
        importPdf184Button.setDisable(true);
        exportCsvButton.setDisable(true);
        reportPdfButton.setDisable(true);
        statusLabel.setText("Status: Wczytywanie i parsowanie pliku PDF Testo 184...");

        // Asynchroniczne zadanie w tle
        Task<TestoUsbImportService.TestoImportResult> task = new Task<>() {
            @Override
            protected TestoUsbImportService.TestoImportResult call() throws Exception {
                return testo184UsbImportService.importFromPdf(file);
            }
        };

        readProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(workerStateEvent -> {
            TestoUsbImportService.TestoImportResult results = task.getValue();

            // Wyłączenie pasków wczytywania
            readProgressIndicator.setVisible(false);
            readProgressIndicator.setManaged(false);
            readProgressBar.setVisible(false);
            readProgressBar.setManaged(false);
            readProgressBar.progressProperty().unbind();
            readUsbButton.setDisable(false);
            importPdf184Button.setDisable(false);

            if ("ERROR".equals(results.status)) {
                log.warn("Odczyt PDF zakończony kodem ERROR: {}", results.message);
                showErrorAlert("Błąd importu PDF", "Nie udało się sparsować pliku PDF", results.message);
                statusLabel.setText("Status: Błąd importu PDF: " + results.message);
                return;
            }

            // Pomyślny odczyt z PDF! Dekodowanie wyników
            pointsList.clear();
            for (TestoUsbImportService.MeasurementPointDto pt : results.measurements) {
                String cleanTime = pt.timestampLocal.length() > 19 ? pt.timestampLocal.substring(0, 19) : pt.timestampLocal;
                pointsList.add(ThermoMeasurementPoint.builder()
                        .measurementIndex(pt.index)
                        .timestampLocal(LocalDateTime.parse(cleanTime))
                        .rawCelsius(pt.valueCelsius)
                        .build());
            }

            // Dane urządzenia
            modelField.setText(results.device.model);
            serialNumberField.setText(results.device.serialNumber);
            batteryLevelField.setText(results.session.batteryLevelPercent >= 0 ? results.session.batteryLevelPercent + "%" : "N/D");
            
            // Metadane sesji
            intervalField.setText(results.session.intervalMinutes + " minut");
            delayField.setText(results.session.startDelayMinutes > 0 ? results.session.startDelayMinutes + " minut" : "Brak opóźnienia");
            countField.setText(String.valueOf(pointsList.size()));

            // Renderowanie interaktywnego wykresu JavaFX
            updateChart();

            exportCsvButton.setDisable(false);
            reportPdfButton.setDisable(false);
            statusLabel.setText("Status: Pomyślnie zaimportowano z raportu PDF: " + pointsList.size() + " punktów.");
            log.info("Pomyślnie wczytano dane z raportu PDF Testo 184.");

            // Zapis do Audit Trail (Historia Odczytów)
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null) ? auth.getName() : "System";
            String details = String.format("Zaimportowano raport PDF z %s (SN: %s). Ilość pomiarów: %d.", 
                    results.device.model, results.device.serialNumber, pointsList.size());
            auditService.logAccessEvent(username, "PDF_IMPORT", details);
        });

        task.setOnFailed(workerStateEvent -> {
            Throwable e = task.getException();
            log.error("Wyjątek podczas odczytu PDF", e);

            readProgressIndicator.setVisible(false);
            readProgressIndicator.setManaged(false);
            readProgressBar.setVisible(false);
            readProgressBar.setManaged(false);
            readProgressBar.progressProperty().unbind();
            readUsbButton.setDisable(false);
            importPdf184Button.setDisable(false);

            statusLabel.setText("Status: Krytyczny błąd odczytu PDF.");
            showErrorAlert("Krytyczny błąd", "Wystąpił błąd podczas parsowania pliku PDF", e != null ? e.getMessage() : "Brak szczegółów");
        });

        new Thread(task).start();
    }

    @FXML
    public void handleClear(ActionEvent event) {
        log.info("Resetowanie widoku odczytu Testo");

        modelField.clear();
        serialNumberField.clear();
        batteryLevelField.clear();
        intervalField.clear();
        delayField.clear();
        countField.clear();
        commentsArea.clear();
        pointsList.clear();

        // Ukryj wykres i wyczyść dane
        chartContainer.setVisible(false);
        chartContainer.setManaged(false);
        temperatureChart.getData().clear();

        exportCsvButton.setDisable(true);
        reportPdfButton.setDisable(true);
        statusLabel.setText("Status: Brak wczytanych danych. Podłącz rejestrator i kliknij \"Odczytaj z USB\".");
    }

    private void showWarningAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ostrzeżenie");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
