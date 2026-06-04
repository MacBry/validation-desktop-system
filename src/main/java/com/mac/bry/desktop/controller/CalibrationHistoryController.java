package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.service.ThermoRecorderService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class CalibrationHistoryController {

    private final ThermoRecorderService recorderService;
    private final ApplicationContext applicationContext;

    @FXML private Label titleLabel;
    @FXML private Label recorderLabel;
    @FXML private TableView<Calibration> calibrationTable;
    @FXML private TableColumn<Calibration, String> dateColumn;
    @FXML private TableColumn<Calibration, String> certNumberColumn;
    @FXML private TableColumn<Calibration, String> validUntilColumn;
    @FXML private TableColumn<Calibration, String> statusColumn;
    @FXML private TableColumn<Calibration, Void> scanColumn;
    @FXML private TableColumn<Calibration, Void> actionsColumn;

    private ThermoRecorder recorder;
    private final ObservableList<Calibration> historyData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCalibrationDate().toString()));
        certNumberColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCertificateNumber()));
        validUntilColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getValidUntil().toString()));
        
        // 1. Fabryka komórek dla statusu - Pill badgi z AtlantaFX
        statusColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isValid() ? "WAŻNE" : "NIEWAŻNE"));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label(item);
                    tagLabel.getStyleClass().add("tag");
                    if ("WAŻNE".equals(item)) {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.SUCCESS);
                    } else {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.DANGER);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });

        setupScanColumn();
        setupActionsColumn();
        calibrationTable.setItems(historyData);
    }

    private void setupScanColumn() {
        scanColumn.setCellFactory(param -> new TableCell<>() {
            private final Button pdfBtn = new Button();
            {
                // Czerwony solidny przycisk podglądu PDF
                pdfBtn.getStyleClass().addAll("button-sm", "danger");
                pdfBtn.setText("Podgląd PDF");
                pdfBtn.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                pdfBtn.setOnAction(e -> handleOpenScan(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Calibration cal = getTableView().getItems().get(getIndex());
                    setGraphic(cal.getCertificateFilePath() != null ? pdfBtn : null);
                }
            }
        });
    }

    private void handleOpenScan(Calibration calibration) {
        if (calibration.getCertificateFilePath() != null) {
            try {
                java.io.File file = new java.io.File(calibration.getCertificateFilePath());
                if (file.exists()) {
                    // Próba otwarcia za pomocą AWT Desktop (może rzucić HeadlessException w niektórych konfiguracjach)
                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().open(file);
                            return;
                        }
                    } catch (Throwable e) {
                        log.warn("java.awt.Desktop.open failed, trying fallback: {}", e.getMessage());
                    }

                    // Fallback dla Windows (cmd /c start)
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("cmd", "/c", "start", "", file.getAbsolutePath()).start();
                    } else {
                        throw new UnsupportedOperationException("Otwieranie plików nie jest obsługiwane na tym systemie bez AWT Desktop.");
                    }
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Nie znaleziono pliku: " + calibration.getCertificateFilePath());
                    alert.show();
                }
            } catch (Exception e) {
                log.error("Błąd podczas otwierania pliku", e);
                Alert alert = new Alert(Alert.AlertType.ERROR, "Nie udało się otworzyć pliku PDF.");
                alert.show();
            }
        }
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = new Button("Szczegóły");
            private final Button deleteBtn = new Button("Usuń");
            private final Button auditBtn = new Button("Audit");
            private final HBox container = new HBox(6, viewBtn, deleteBtn, auditBtn);

            {
                // Ujednolicenie przycisków w tabeli - styl solid, mini oraz intencje kolorystyczne
                viewBtn.getStyleClass().addAll("button-sm", "success");
                deleteBtn.getStyleClass().addAll("button-sm", "danger");
                auditBtn.getStyleClass().addAll("button-sm", "accent");
                
                viewBtn.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
                auditBtn.setOnAction(e -> handleShowAudit(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });
    }

    public void setRecorder(ThermoRecorder recorder) {
        this.recorder = recorder;
        recorderLabel.setText("Rejestrator: " + recorder.getSerialNumber() + " (" + recorder.getModel() + ")");
        loadHistory();
    }

    private void loadHistory() {
        List<Calibration> history = recorderService.getCalibrationsForRecorder(recorder.getId());
        historyData.setAll(history);
    }

    @FXML
    public void handleAddCalibration() {
        openCalibrationDialog(null);
    }

    private void handleEdit(Calibration calibration) {
        openCalibrationDialog(calibration);
    }

    private void openCalibrationDialog(Calibration calibration) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/calibration_dialog.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            CalibrationDialogController controller = loader.getController();
            if (calibration == null) {
                controller.setRecorder(recorder);
            } else {
                controller.setCalibration(calibration);
            }

            Stage stage = new Stage();
            stage.setTitle(calibration == null ? "Nowe Wzorcowanie" : "Edycja Wzorcowania");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                loadHistory();
            }
        } catch (IOException e) {
            log.error("Błąd podczas otwierania okna wzorcowania", e);
        }
    }

    private void handleShowAudit(Calibration calibration) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/user_audit.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            UserAuditController controller = loader.getController();
            controller.initCalibrationData(calibration);

            Stage stage = new Stage();
            stage.setTitle("Audit Trail: " + calibration.getCertificateNumber());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            log.error("Błąd podczas otwierania audytu świadectwa", e);
        }
    }

    private void handleDelete(Calibration calibration) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Usuwanie wzorcowania");
        alert.setHeaderText("Czy na pewno chcesz usunąć to wzorcowanie?");
        alert.setContentText("Numer świadectwa: " + calibration.getCertificateNumber());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                recorderService.deleteCalibration(calibration.getId());
                loadHistory();
            } catch (Exception e) {
                log.error("Błąd podczas usuwania wzorcowania", e);
                Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Nie udało się usunąć wzorcowania.");
                errorAlert.show();
            }
        }
    }

    @FXML
    public void handleClose() {
        ((Stage) titleLabel.getScene().getWindow()).close();
    }
}
