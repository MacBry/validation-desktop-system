package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.CalibrationPoint;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.repository.CalibrationRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class CalibrationDialogController {

    private final CalibrationRepository calibrationRepository;

    @FXML private TextField certNumberField;
    @FXML private DatePicker calibrationDatePicker;
    @FXML private DatePicker validUntilPicker;
    @FXML private CheckBox autoDateCheckBox;
    @FXML private TextField certFilePathField;
    
    @FXML private TableView<CalibrationPoint> pointsTable;
    @FXML private TableColumn<CalibrationPoint, BigDecimal> tempColumn;
    @FXML private TableColumn<CalibrationPoint, BigDecimal> errorColumn;
    @FXML private TableColumn<CalibrationPoint, BigDecimal> uncertaintyColumn;
    @FXML private TableColumn<CalibrationPoint, Void> deletePointColumn;

    private ThermoRecorder recorder;
    private Calibration calibration;
    private boolean saved = false;
    private final ObservableList<CalibrationPoint> pointsData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        setupDateHandling();
    }

    private final StringConverter<BigDecimal> bigDecimalConverter = new StringConverter<>() {
        @Override
        public String toString(BigDecimal object) {
            return object == null ? "" : object.toString();
        }

        @Override
        public BigDecimal fromString(String string) {
            if (string == null || string.isBlank()) return BigDecimal.ZERO;
            try {
                return new BigDecimal(string.replace(",", "."));
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
    };

    private TextFieldTableCell<CalibrationPoint, BigDecimal> createCommitOnFocusLostCell() {
        return new TextFieldTableCell<>(bigDecimalConverter) {
            @Override
            public void startEdit() {
                super.startEdit();
                Control control = (Control) getGraphic();
                if (control instanceof TextField textField) {
                    textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                        if (!newVal && isEditing()) {
                            commitEdit(bigDecimalConverter.fromString(textField.getText()));
                        }
                    });
                }
            }
        };
    }

    private void setupTable() {
        tempColumn.setCellValueFactory(new PropertyValueFactory<>("temperatureValue"));
        tempColumn.setCellFactory(col -> createCommitOnFocusLostCell());
        tempColumn.setOnEditCommit(e -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setTemperatureValue(e.getNewValue()));

        errorColumn.setCellValueFactory(new PropertyValueFactory<>("systematicError"));
        errorColumn.setCellFactory(col -> createCommitOnFocusLostCell());
        errorColumn.setOnEditCommit(e -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setSystematicError(e.getNewValue()));

        uncertaintyColumn.setCellValueFactory(new PropertyValueFactory<>("uncertainty"));
        uncertaintyColumn.setCellFactory(col -> createCommitOnFocusLostCell());
        uncertaintyColumn.setOnEditCommit(e -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setUncertainty(e.getNewValue()));

        deletePointColumn.setCellFactory(param -> new TableCell<>() {
            private final Button deleteBtn = new Button("Usuń");
            {
                deleteBtn.getStyleClass().addAll("button-sm", "danger");
                deleteBtn.setOnAction(e -> pointsData.remove(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        pointsTable.setItems(pointsData);
    }

    private void setupDateHandling() {
        calibrationDatePicker.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && autoDateCheckBox.isSelected()) {
                validUntilPicker.setValue(newVal.plusYears(1));
            }
        });
    }

    public void setRecorder(ThermoRecorder recorder) {
        this.recorder = recorder;
        this.calibration = new Calibration();
        this.calibration.setThermoRecorder(recorder);
        this.pointsData.clear();
        calibrationDatePicker.setValue(LocalDate.now());
    }

    public void setCalibration(Calibration calibration) {
        this.calibration = calibration;
        this.recorder = calibration.getThermoRecorder();
        
        certNumberField.setText(calibration.getCertificateNumber());
        calibrationDatePicker.setValue(calibration.getCalibrationDate());
        validUntilPicker.setValue(calibration.getValidUntil());
        
        this.pointsData.setAll(calibration.getPoints());
        autoDateCheckBox.setSelected(false);
        certFilePathField.setText(calibration.getCertificateFilePath() != null ? calibration.getCertificateFilePath() : "");
    }

    @FXML
    public void handleBrowseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Wybierz skan świadectwa (PDF)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pliki PDF", "*.pdf"));
        
        java.io.File file = fileChooser.showOpenDialog(certNumberField.getScene().getWindow());
        if (file != null) {
            certFilePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void handleAddPoint() {
        CalibrationPoint point = new CalibrationPoint();
        point.setTemperatureValue(BigDecimal.ZERO);
        point.setSystematicError(BigDecimal.ZERO);
        point.setUncertainty(new BigDecimal("0.05"));
        calibration.addPoint(point);
        pointsData.add(point);
    }

    @FXML
    public void handleSave() {
        try {
            calibration.setCertificateNumber(certNumberField.getText());
            calibration.setCalibrationDate(calibrationDatePicker.getValue());
            calibration.setValidUntil(validUntilPicker.getValue());
            calibration.setPoints(new java.util.ArrayList<>(pointsData));

            if (calibration.getCertificateNumber().isEmpty() || calibration.getCalibrationDate() == null) {
                log.error("Numer i data są wymagane");
                return;
            }

            // Obsługa kopiowania pliku jeśli ścieżka się zmieniła
            String selectedPath = certFilePathField.getText();
            if (selectedPath != null && !selectedPath.isEmpty() && !selectedPath.equals(calibration.getCertificateFilePath())) {
                java.io.File source = new java.io.File(selectedPath);
                if (source.exists()) {
                    String fileName = "CERT_" + calibration.getCertificateNumber().replaceAll("[^a-zA-Z0-9.-]", "_") + "_" + System.currentTimeMillis() + ".pdf";
                    java.io.File destDir = new java.io.File("uploads/certificates");
                    if (!destDir.exists()) destDir.mkdirs();
                    
                    java.io.File destFile = new java.io.File(destDir, fileName);
                    java.nio.file.Files.copy(source.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    calibration.setCertificateFilePath(destFile.getPath());
                }
            }

            calibrationRepository.save(calibration);
            saved = true;
            close();
        } catch (Exception e) {
            log.error("Błąd podczas zapisu wzorcowania", e);
        }
    }

    @FXML
    public void handleCancel() {
        close();
    }

    private void close() {
        ((Stage) certNumberField.getScene().getWindow()).close();
    }

    public boolean isSaved() {
        return saved;
    }
}
