package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.ChamberType;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.DeviceStatus;
import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.service.CoolingDeviceService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceDialogController {

    private final CoolingDeviceService coolingDeviceService;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ApplicationContext applicationContext;

    @FXML private Label dialogTitleLabel;
    @FXML private Label dialogSubtitleLabel;
    @FXML private TextField inventoryField;
    @FXML private TextField nameField;
    @FXML private ComboBox<Department> departmentCombo;
    @FXML private ComboBox<Laboratory> laboratoryCombo;
    @FXML private ComboBox<DeviceStatus> statusCombo;
    
    // Tabela komór i kontrolki
    @FXML private TableView<CoolingChamber> chambersTable;
    @FXML private TableColumn<CoolingChamber, String> chamberNameCol;
    @FXML private TableColumn<CoolingChamber, String> chamberTypeCol;
    @FXML private TableColumn<CoolingChamber, String> chamberRangeCol;
    @FXML private TableColumn<CoolingChamber, String> chamberVolumeCol;
    @FXML private TableColumn<CoolingChamber, String> chamberMaterialCol;
    @FXML private Button addChamberButton;
    @FXML private Button editChamberButton;
    @FXML private Button deleteChamberButton;
    @FXML private Button saveButton;

    private CoolingDevice coolingDevice;
    private boolean isEdit = false;
    private boolean readOnly = false;
    private boolean saved = false;

    private final ObservableList<CoolingChamber> chambersList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupConverters();
        setupStatusCombo();
        setupListeners();
        setupChambersTable();
    }

    public void setDevice(CoolingDevice device, boolean isEdit, boolean readOnly) {
        this.coolingDevice = device;
        this.isEdit = isEdit;
        this.readOnly = readOnly;

        populateCombos();

        if (readOnly) {
            dialogTitleLabel.setText("Szczegóły Urządzenia");
            dialogSubtitleLabel.setText("Widok parametrów technicznych w trybie tylko do odczytu.");
            disableAllFields();
        } else if (isEdit) {
            dialogTitleLabel.setText("Edycja Urządzenia Chłodniczego");
            dialogSubtitleLabel.setText("Modyfikujesz zarejestrowane urządzenie chłodnicze.");
            inventoryField.setDisable(true); // Zablokowany unikalny numer inwentarzowy przy edycji
        }

        if (device.getId() != null) {
            inventoryField.setText(device.getInventoryNumber());
            nameField.setText(device.getName());
            departmentCombo.setValue(device.getDepartment());
            laboratoryCombo.setValue(device.getLaboratory());
            statusCombo.setValue(device.getStatus() != null ? device.getStatus() : DeviceStatus.ACTIVE);
            
            // Ładowanie komór
            if (device.getChambers() != null) {
                chambersList.setAll(device.getChambers());
            }
        } else {
            statusCombo.setValue(DeviceStatus.ACTIVE);
        }
    }

    public boolean isSaved() {
        return saved;
    }

    private void setupStatusCombo() {
        statusCombo.setItems(javafx.collections.FXCollections.observableArrayList(DeviceStatus.values()));
        statusCombo.setConverter(new StringConverter<>() {
            @Override public String toString(DeviceStatus s) { return s == null ? "" : s.getDisplayName(); }
            @Override public DeviceStatus fromString(String s) { return null; }
        });
    }

    private void setupConverters() {
        // Konwerter dla Działów
        departmentCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Department object) {
                return object != null ? object.getName() + " (" + object.getAbbreviation() + ")" : "";
            }

            @Override
            public Department fromString(String string) {
                return null;
            }
        });

        // Konwerter dla Pracowni
        laboratoryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Laboratory object) {
                return object != null ? object.getName() + " (" + object.getAbbreviation() + ")" : "";
            }

            @Override
            public Laboratory fromString(String string) {
                return null;
            }
        });
    }

    private void setupListeners() {
        // Zmiana działu filtruje pracownie
        departmentCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                List<Laboratory> labs = laboratoryRepository.findByDepartmentId(newVal.getId());
                laboratoryCombo.setItems(FXCollections.observableArrayList(labs));
                if (!labs.contains(laboratoryCombo.getValue())) {
                    laboratoryCombo.setValue(null);
                }
            } else {
                laboratoryCombo.setItems(FXCollections.emptyObservableList());
                laboratoryCombo.setValue(null);
            }
        });
    }

    private void setupChambersTable() {
        chamberNameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getChamberName()));
        chamberTypeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getChamberType().getDisplayName()));
        
        chamberRangeCol.setCellValueFactory(cell -> {
            Double min = cell.getValue().getMinOperatingTemp();
            Double max = cell.getValue().getMaxOperatingTemp();
            if (min == null && max == null) return new SimpleStringProperty("–");
            return new SimpleStringProperty((min != null ? min : "–") + "°C do " + (max != null ? max : "–") + "°C");
        });
        
        chamberVolumeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFormattedVolume()));
        chamberMaterialCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMaterialName()));

        chambersTable.setItems(chambersList);

        // Dwukrotne kliknięcie otwiera edycję komory
        chambersTable.setRowFactory(tv -> {
            TableRow<CoolingChamber> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    handleEditChamber();
                }
            });
            return row;
        });
    }

    private void populateCombos() {
        departmentCombo.setItems(FXCollections.observableArrayList(departmentRepository.findAll()));
    }

    private void disableAllFields() {
        inventoryField.setDisable(true);
        nameField.setDisable(true);
        departmentCombo.setDisable(true);
        laboratoryCombo.setDisable(true);
        statusCombo.setDisable(true);

        addChamberButton.setVisible(false);
        addChamberButton.setManaged(false);
        editChamberButton.setVisible(false);
        editChamberButton.setManaged(false);
        deleteChamberButton.setVisible(false);
        deleteChamberButton.setManaged(false);

        saveButton.setVisible(false);
        saveButton.setManaged(false);
    }

    @FXML
    private void handleAddChamber() {
        CoolingChamber newChamber = new CoolingChamber();
        newChamber.setCoolingDevice(coolingDevice);
        openChamberDialog(newChamber, false, false);
    }

    @FXML
    private void handleEditChamber() {
        CoolingChamber selected = chambersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Brak wyboru", "Proszę wybrać komorę z tabeli do edycji.");
            return;
        }
        openChamberDialog(selected, true, readOnly);
    }

    @FXML
    private void handleDeleteChamber() {
        CoolingChamber selected = chambersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Brak wyboru", "Proszę wybrać komorę z tabeli do usunięcia.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potwierdzenie usunięcia");
        confirm.setHeaderText("Czy na pewno chcesz usunąć komorę?");
        confirm.setContentText("Komora: " + selected.getChamberName() + "\nTa zmiana zostanie zapisana dopiero po zatwierdzeniu całego urządzenia.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                chambersList.remove(selected);
            }
        });
    }

    private void openChamberDialog(CoolingChamber chamber, boolean isChamberEdit, boolean isChamberReadOnly) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/cooling_chamber_dialog.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            CoolingChamberDialogController controller = loader.getController();
            controller.setChamber(chamber, isChamberEdit, isChamberReadOnly);

            Stage stage = new Stage();
            stage.setTitle(isChamberReadOnly ? "Szczegóły Komory" : (isChamberEdit ? "Edycja Komory" : "Nowa Komora"));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(saveButton.getScene().getWindow());
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                if (!isChamberEdit) {
                    chambersList.add(chamber);
                } else {
                    chambersTable.refresh();
                }
            }
        } catch (IOException e) {
            log.error("Błąd podczas otwierania okna dialogowego komory chłodniczej", e);
            showError("Błąd krytyczny", "Nie udało się załadować okna komory: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        if (readOnly) return;

        if (validateFields()) {
            coolingDevice.setInventoryNumber(inventoryField.getText().trim());
            coolingDevice.setName(nameField.getText().trim());
            coolingDevice.setDepartment(departmentCombo.getValue());
            coolingDevice.setLaboratory(laboratoryCombo.getValue());
            coolingDevice.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : DeviceStatus.ACTIVE);

            // 1. Walidacja biznesowa - urządzenie chłodnicze musi mieć przynajmniej jedną komorę!
            if (chambersList.isEmpty()) {
                showError("Błąd zapisu", "Urządzenie chłodnicze musi posiadać co najmniej jedną zdefiniowaną komorę!");
                return;
            }

            // 2. Synchronizacja komór w obiekcie (bezpieczne usuwanie sierot i dodawanie nowych)
            List<CoolingChamber> existingChambers = new ArrayList<>(coolingDevice.getChambers());
            for (CoolingChamber original : existingChambers) {
                if (!chambersList.contains(original)) {
                    coolingDevice.removeChamber(original);
                }
            }
            for (CoolingChamber c : chambersList) {
                if (!coolingDevice.getChambers().contains(c)) {
                    coolingDevice.addChamber(c);
                }
            }

            try {
                // Sprawdzenie unikalności numeru inwentarzowego
                if (!isEdit && coolingDeviceService.existsByInventoryNumber(coolingDevice.getInventoryNumber())) {
                    showError("Błąd zapisu", "Urządzenie o podanym numerze inwentarzowym już istnieje w bazie danych!");
                    return;
                }

                coolingDeviceService.save(coolingDevice);
                saved = true;
                handleCancel();

            } catch (Exception ex) {
                log.error("Błąd podczas zapisywania urządzenia", ex);
                showError("Błąd bazy danych", "Nie udało się zapisać urządzenia: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) inventoryField.getScene().getWindow();
        stage.close();
    }

    private boolean validateFields() {
        StringBuilder errorMsg = new StringBuilder();

        if (inventoryField.getText().trim().isEmpty()) {
            errorMsg.append("- Numer inwentarzowy jest wymagany.\n");
        }
        if (nameField.getText().trim().isEmpty()) {
            errorMsg.append("- Nazwa urządzenia jest wymagana.\n");
        }
        if (departmentCombo.getValue() == null) {
            errorMsg.append("- Wybór działu jest wymagany.\n");
        }

        if (errorMsg.length() > 0) {
            showError("Brakujące lub błędne dane", "Proszę poprawić następujące pola:\n" + errorMsg.toString());
            return false;
        }
        return true;
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
