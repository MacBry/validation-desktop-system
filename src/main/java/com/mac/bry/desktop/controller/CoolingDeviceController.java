package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.*;
import com.mac.bry.desktop.controller.helper.CoolingDeviceCellFactoryHelper;
import com.mac.bry.desktop.controller.helper.CoolingDeviceTableHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceController {

    private final CoolingDeviceService coolingDeviceService;
    private final CoolingDeviceSecurityService securityService;
    private final TestoRevalidationService testoRevalidationService;
    private final ApplicationContext applicationContext;
    private final ValidationPlanNumberRepository validationPlanNumberRepository;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> chamberFilter;
    
    // Tabela główna (Master)
    @FXML private TableView<CoolingDevice> deviceTable;
    @FXML private TableColumn<CoolingDevice, String> inventoryCol;
    @FXML private TableColumn<CoolingDevice, String> nameCol;
    @FXML private TableColumn<CoolingDevice, String> deptCol;
    @FXML private TableColumn<CoolingDevice, String> chambersCountCol;
    @FXML private TableColumn<CoolingDevice, String> statusCol;
    @FXML private TableColumn<CoolingDevice, Void> actionsCol;

    // Tabela szczegółowa (Detail)
    @FXML private Label chambersTitleLabel;
    @FXML private TableView<CoolingChamber> chambersDetailsTable;
    @FXML private TableColumn<CoolingChamber, String> detChamberNameCol;
    @FXML private TableColumn<CoolingChamber, String> detChamberTypeCol;
    @FXML private TableColumn<CoolingChamber, String> detChamberRangeCol;
    @FXML private TableColumn<CoolingChamber, String> detChamberVolumeCol;
    @FXML private TableColumn<CoolingChamber, String> detChamberPdaCol;
    @FXML private TableColumn<CoolingChamber, String> detChamberRevalCol;
    @FXML private TableColumn<CoolingChamber, String> detChamberMaterialCol;

    @FXML private Label deviceCountLabel;
    @FXML private Button addDeviceButton;

    // RPW Tab Components
    @FXML private Label rpwTitleLabel;
    @FXML private Button addRpwButton;
    @FXML private Button deleteRpwButton;
    @FXML private TableView<com.mac.bry.desktop.model.ValidationPlanNumber> rpwTable;
    @FXML private TableColumn<com.mac.bry.desktop.model.ValidationPlanNumber, Integer> rpwYearCol;
    @FXML private TableColumn<com.mac.bry.desktop.model.ValidationPlanNumber, Integer> rpwNumberCol;
    @FXML private TableColumn<com.mac.bry.desktop.model.ValidationPlanNumber, String> rpwFormattedCol;

    private final ObservableList<CoolingDevice> masterData = FXCollections.observableArrayList();
    private boolean isAdmin = false;

    @FXML
    public void initialize() {
        log.info("Initializing CoolingDeviceController");
        isAdmin = securityService.isUserAdmin();
        addDeviceButton.setVisible(isAdmin);
        addDeviceButton.setManaged(isAdmin);
        log.debug("Admin button visibility configured: {}", isAdmin);

        CoolingDeviceTableHelper.setupMasterTable(deviceTable, inventoryCol, nameCol, deptCol, chambersCountCol, statusCol);
        CoolingDeviceTableHelper.setupDetailTable(chambersDetailsTable, detChamberNameCol, detChamberTypeCol,
                                      detChamberRangeCol, detChamberVolumeCol, detChamberMaterialCol);

        // Setup RPW table columns
        rpwYearCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("year"));
        rpwNumberCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("planNumber"));
        rpwFormattedCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFormattedRpw()));

        // Bind disable properties for admin buttons
        addRpwButton.setVisible(isAdmin);
        addRpwButton.setManaged(isAdmin);
        deleteRpwButton.setVisible(isAdmin);
        deleteRpwButton.setManaged(isAdmin);
        addRpwButton.disableProperty().bind(deviceTable.getSelectionModel().selectedItemProperty().isNull());
        deleteRpwButton.disableProperty().bind(rpwTable.getSelectionModel().selectedItemProperty().isNull());

        CoolingDeviceCellFactoryHelper.setupVolumeCategoryCell(detChamberPdaCol);
        CoolingDeviceCellFactoryHelper.setupRevalidationStatusCell(detChamberRevalCol, testoRevalidationService::getRevalidationStatusText);

        setupActionsColumn();
        CoolingDeviceTableHelper.setupFilters(searchField, chamberFilter, masterData, deviceTable);
        setupSelectionListener();

        handleRefresh();
        log.info("CoolingDeviceController initialization completed");
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = new Button("Podgląd");
            private final Button editBtn = new Button("Edytuj");
            private final Button deleteBtn = new Button("Usuń");
            private final Button auditBtn = new Button("Audit");
            private final HBox container = new HBox(6);

            {
                viewBtn.getStyleClass().addAll("button-sm");
                editBtn.getStyleClass().addAll("button-sm", "success");
                deleteBtn.getStyleClass().addAll("button-sm", "danger");
                auditBtn.getStyleClass().addAll("button-sm", "accent");

                viewBtn.setOnAction(e -> handleViewDevice(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e -> handleEditDevice(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDeleteDevice(getTableView().getItems().get(getIndex())));
                auditBtn.setOnAction(e -> handleShowAudit(getTableView().getItems().get(getIndex())));

                if (isAdmin) {
                    container.getChildren().addAll(editBtn, deleteBtn, auditBtn);
                } else {
                    container.getChildren().addAll(viewBtn, auditBtn);
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });
    }

    private void setupSelectionListener() {
        deviceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (chambersTitleLabel != null) {
                    chambersTitleLabel.setText("Komory urządzenia: " + newVal.getName() + " (" + newVal.getInventoryNumber() + ")");
                }
                if (rpwTitleLabel != null) {
                    rpwTitleLabel.setText("Historia planów walidacji: " + newVal.getName() + " (" + newVal.getInventoryNumber() + ")");
                }
                if (newVal.getChambers() != null) {
                    chambersDetailsTable.setItems(FXCollections.observableArrayList(newVal.getChambers()));
                } else {
                    chambersDetailsTable.setItems(FXCollections.emptyObservableList());
                }
                loadRpwData(newVal);
            } else {
                if (chambersTitleLabel != null) {
                    chambersTitleLabel.setText("Komory wybranego urządzenia");
                }
                if (rpwTitleLabel != null) {
                    rpwTitleLabel.setText("Historia planów walidacji urządzenia");
                }
                chambersDetailsTable.setItems(FXCollections.emptyObservableList());
                rpwTable.setItems(FXCollections.emptyObservableList());
            }
        });
    }

    @FXML
    public void handleRefresh() {
        List<CoolingDevice> devices = coolingDeviceService.findAll();
        masterData.setAll(devices);
        deviceCountLabel.setText("Znaleziono: " + masterData.size() + " urządzeń");
        
        // Reset zaznaczenia
        deviceTable.getSelectionModel().clearSelection();
    }

    @FXML
    public void handleAddNewDevice() {
        if (!isAdmin) return;
        openDeviceDialog(new CoolingDevice(), false, false);
    }

    private void handleEditDevice(CoolingDevice device) {
        if (!isAdmin) return;
        openDeviceDialog(device, true, false);
    }

    private void handleViewDevice(CoolingDevice device) {
        openDeviceDialog(device, false, true);
    }

    private void handleDeleteDevice(CoolingDevice device) {
        if (!isAdmin) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potwierdzenie usunięcia");
        confirm.setHeaderText("Czy na pewno chcesz usunąć to urządzenie chłodnicze?");
        confirm.setContentText("Urządzenie: " + device.getName() + " (" + device.getInventoryNumber() + ")\nTa operacja usunie również wszystkie jego komory i jest nieodwracalna!");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    coolingDeviceService.deleteById(device.getId());
                    handleRefresh();
                } catch (Exception ex) {
                    log.error("Błąd podczas usuwania urządzenia", ex);
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Błąd");
                    err.setHeaderText("Nie można usunąć urządzenia");
                    err.setContentText(ex.getMessage());
                    err.show();
                }
            }
        });
    }

    private void handleShowAudit(CoolingDevice device) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/user_audit.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            UserAuditController controller = loader.getController();
            
            try {
                controller.initCoolingDeviceData(device);
            } catch (Exception ex) {
                log.warn("UserAuditController init error, fallback method", ex);
                controller.initRecorderData(null); 
            }

            Stage stage = new Stage();
            stage.setTitle("Audit Trail: " + device.getInventoryNumber());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            log.error("Błąd podczas otwierania audytu komory chłodniczej", e);
        }
    }

    private void openDeviceDialog(CoolingDevice device, boolean isEdit, boolean readOnly) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/cooling_device_dialog.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            CoolingDeviceDialogController controller = loader.getController();
            controller.setDevice(device, isEdit, readOnly);

            Stage stage = new Stage();
            stage.setTitle(readOnly ? "Szczegóły Urządzenia" : (isEdit ? "Edycja Urządzenia" : "Nowe Urządzenie"));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                handleRefresh();
            }
        } catch (IOException e) {
            log.error("Błąd podczas otwierania okna dialogowego urządzenia chłodniczego", e);
        }
    }

    private void loadRpwData(CoolingDevice device) {
        if (device != null && device.getId() != null) {
            List<com.mac.bry.desktop.model.ValidationPlanNumber> list = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(device);
            rpwTable.setItems(FXCollections.observableArrayList(list));
        } else {
            rpwTable.setItems(FXCollections.emptyObservableList());
        }
    }

    @FXML
    public void handleAddRpw() {
        if (!isAdmin) return;
        CoolingDevice selectedDevice = deviceTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Dodaj numer RPW");
        dialog.setHeaderText("Wprowadź dane dla nowego rocznego planu walidacji");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(deviceTable.getScene().getWindow());

        ButtonType saveButtonType = new ButtonType("Zapisz", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField yearField = new TextField();
        yearField.setPromptText("np. 2026");
        TextField numberField = new TextField();
        numberField.setPromptText("np. 42");

        grid.add(new Label("Rok:"), 0, 0);
        grid.add(yearField, 1, 0);
        grid.add(new Label("Numer RPW:"), 0, 1);
        grid.add(numberField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        final Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String yearText = yearField.getText().trim();
            String numberText = numberField.getText().trim();

            if (yearText.isEmpty() || numberText.isEmpty()) {
                showValidationError("Błąd walidacji", "Wszystkie pola są wymagane.");
                event.consume();
                return;
            }

            try {
                int year = Integer.parseInt(yearText);
                int number = Integer.parseInt(numberText);

                if (year < 2000 || year > 2100) {
                    showValidationError("Błąd walidacji", "Rok musi być z przedziału 2000 - 2100.");
                    event.consume();
                    return;
                }
                if (number <= 0) {
                    showValidationError("Błąd walidacji", "Numer RPW musi być większy od zera.");
                    event.consume();
                    return;
                }

                coolingDeviceService.addValidationPlanNumber(selectedDevice.getId(), year, number);

            } catch (NumberFormatException e) {
                showValidationError("Błąd walidacji", "Rok i numer RPW muszą być liczbami całkowitymi.");
                event.consume();
            } catch (Exception e) {
                showValidationError("Błąd zapisu", e.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == saveButtonType) {
                loadRpwData(selectedDevice);
            }
        });
    }

    @FXML
    public void handleDeleteRpw() {
        if (!isAdmin) return;
        CoolingDevice selectedDevice = deviceTable.getSelectionModel().getSelectedItem();
        com.mac.bry.desktop.model.ValidationPlanNumber selectedRpw = rpwTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null || selectedRpw == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potwierdzenie usunięcia");
        confirm.setHeaderText("Czy na pewno chcesz usunąć wybrany plan RPW?");
        confirm.setContentText("Numer planu: " + selectedRpw.getFormattedRpw() + "\nTa operacja jest nieodwracalna!");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    coolingDeviceService.removeValidationPlanNumber(selectedDevice.getId(), selectedRpw.getId());
                    loadRpwData(selectedDevice);
                } catch (Exception ex) {
                    log.error("Błąd podczas usuwania planu RPW", ex);
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Błąd");
                    err.setHeaderText("Nie można usunąć planu RPW");
                    err.setContentText(ex.getMessage());
                    err.show();
                }
            }
        });
    }

    private void showValidationError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
