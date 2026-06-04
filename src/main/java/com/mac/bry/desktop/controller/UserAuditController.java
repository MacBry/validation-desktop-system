package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.UserAuditDto;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.service.AuditService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@FxmlView("/ui/user_audit.fxml")
@RequiredArgsConstructor
public class UserAuditController {

    private final AuditService auditService;
    private final com.mac.bry.desktop.service.ExportService exportService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private User currentUser;

    @FXML private Label mainTitleLabel;
    @FXML private Label userInfoLabel;
    @FXML private TableView<UserAuditDto> auditTable;
    @FXML private TableColumn<UserAuditDto, String> dateColumn;
    @FXML private TableColumn<UserAuditDto, String> adminColumn;
    @FXML private TableColumn<UserAuditDto, String> typeColumn;
    @FXML private TableColumn<UserAuditDto, String> fieldColumn;
    @FXML private TableColumn<UserAuditDto, String> oldValueColumn;
    @FXML private TableColumn<UserAuditDto, String> newValueColumn;

    @FXML
    public void initialize() {
        dateColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTimestamp().format(formatter)));
        adminColumn.setCellValueFactory(new PropertyValueFactory<>("modifiedBy"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("operationType"));
        fieldColumn.setCellValueFactory(new PropertyValueFactory<>("fieldName"));
        oldValueColumn.setCellValueFactory(new PropertyValueFactory<>("oldValue"));
        newValueColumn.setCellValueFactory(new PropertyValueFactory<>("newValue"));
    }

    public void initData(User user) {
        this.currentUser = user;
        mainTitleLabel.setText("Historia Audytu Użytkownika");
        userInfoLabel.setText("Użytkownik: " + user.getUsername() + " (" + user.getFirstName() + " " + user.getLastName() + ")");
        loadHistory(User.class, user.getId(), "Konto");
    }

    public void initDeptData(com.mac.bry.desktop.security.model.Department dept) {
        mainTitleLabel.setText("Historia Audytu Działu");
        userInfoLabel.setText("Dział: " + dept.getName() + " (" + dept.getAbbreviation() + ")");
        loadHistory(com.mac.bry.desktop.security.model.Department.class, dept.getId(), "Dział");
    }

    public void initLabData(com.mac.bry.desktop.security.model.Laboratory lab) {
        mainTitleLabel.setText("Historia Audytu Pracowni");
        userInfoLabel.setText("Pracownia: " + lab.getName() + " (" + lab.getAbbreviation() + ")");
        loadHistory(com.mac.bry.desktop.security.model.Laboratory.class, lab.getId(), "Pracownia");
    }

    public void initRecorderData(com.mac.bry.desktop.model.ThermoRecorder recorder) {
        mainTitleLabel.setText("Historia Audytu Rejestratora");
        userInfoLabel.setText("Rejestrator: " + recorder.getSerialNumber() + " (" + recorder.getModel() + ")");
        loadHistory(com.mac.bry.desktop.model.ThermoRecorder.class, recorder.getId(), "Rejestrator");
    }

    public void initCalibrationData(com.mac.bry.desktop.model.Calibration calibration) {
        mainTitleLabel.setText("Historia Audytu Świadectwa");
        userInfoLabel.setText("Świadectwo: " + calibration.getCertificateNumber());
        loadHistory(com.mac.bry.desktop.model.Calibration.class, calibration.getId(), "Świadectwo");
    }

    public void initCoolingDeviceData(com.mac.bry.desktop.model.CoolingDevice device) {
        mainTitleLabel.setText("Historia Audytu Urządzenia Chłodniczego");
        userInfoLabel.setText("Urządzenie: " + device.getName() + " (" + device.getInventoryNumber() + ")");
        loadHistory(com.mac.bry.desktop.model.CoolingDevice.class, device.getId(), "Urządzenie Chłodnicze");
    }

    public void initMaterialData(com.mac.bry.desktop.model.MaterialType material) {
        mainTitleLabel.setText("Historia Audytu Typu Materiału");
        userInfoLabel.setText("Materiał: " + material.getName() + " (" + material.getTemperatureRange() + ")");
        loadHistory(com.mac.bry.desktop.model.MaterialType.class, material.getId(), "Typ Materiału");
    }

    private void loadHistory(Class<?> entityClass, Long id, String entityName) {
        java.util.List<UserAuditDto> history = auditService.getEntityHistory(entityClass, id, entityName);
        auditTable.setItems(FXCollections.observableArrayList(history));
    }

    @FXML
    private void handleExportPdf() {
        String entityInfo = userInfoLabel.getText();
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Zapisz Raport Audytu (PDF)");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Pliki PDF", "*.pdf"));
        fileChooser.setInitialFileName("Audyt_" + entityInfo.replaceAll("[^a-zA-Z0-9]", "_") + "_" + java.time.LocalDate.now() + ".pdf");
        
        java.io.File file = fileChooser.showSaveDialog(auditTable.getScene().getWindow());
        if (file != null) {
            try {
                exportService.exportToPdf(auditTable.getItems(), file, "Raport Historii Audytu - " + entityInfo);
                showInfo("Sukces", "Raport PDF został wygenerowany pomyślnie.");
            } catch (java.io.IOException e) {
                showError("Błąd eksportu", "Nie udało się wygenerować pliku PDF: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportCsv() {
        String entityInfo = userInfoLabel.getText();
        
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Zapisz Raport Audytu (CSV)");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Pliki CSV", "*.csv"));
        fileChooser.setInitialFileName("Audyt_" + entityInfo.replaceAll("[^a-zA-Z0-9]", "_") + "_" + java.time.LocalDate.now() + ".csv");
        
        java.io.File file = fileChooser.showSaveDialog(auditTable.getScene().getWindow());
        if (file != null) {
            try {
                exportService.exportToCsv(auditTable.getItems(), file);
                showInfo("Sukces", "Dane CSV zostały wyeksportowane pomyślnie.");
            } catch (java.io.IOException e) {
                showError("Błąd eksportu", "Nie udało się wygenerować pliku CSV: " + e.getMessage());
            }
        }
    }

    private void showInfo(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) auditTable.getScene().getWindow();
        stage.close();
    }
}
