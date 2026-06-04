# Plan Refaktoryzacji: CoolingDeviceController

## Przegląd

Optymalizacja `CoolingDeviceController` (378 linii) - ta klasa jest mniej problematyczna niż DashboardController, ale można ją ulepszyć poprzez wyekstrahowanie logiki table setup i cell factory.

## Architektura Po Refaktoryzacji

```
CoolingDeviceController (refactored - ~240 linii)
└── Orchestration, dialog management, CRUD handlers

CoolingDeviceTableService (100-120 linii)
├── setupMasterTable()
├── setupDetailTable()
├── setupActionsColumn()
└── setupFilters()

CoolingDeviceCellFactoryService (80-100 linii)
├── createVolumeCategoryCell()
├── createRevalidationStatusCell()
└── createActionButtonsCell()

CoolingDeviceSecurityService (40-50 linii)
├── checkUserRole()
└── isUserAdmin()
```

---

## Krok 1: CoolingDeviceTableService

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ChamberType;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.CoolingDevice;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceTableService {

    private final TestoRevalidationService revalidationService;

    public void setupMasterTable(
            TableView<CoolingDevice> deviceTable,
            TableColumn<CoolingDevice, String> inventoryCol,
            TableColumn<CoolingDevice, String> nameCol,
            TableColumn<CoolingDevice, String> deptCol,
            TableColumn<CoolingDevice, String> chambersCountCol) {
        
        inventoryCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getInventoryNumber()));
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        
        deptCol.setCellValueFactory(d -> {
            String deptName = d.getValue().getDepartment() != null ? d.getValue().getDepartment().getName() : "-";
            String labName = d.getValue().getLaboratory() != null ? " / " + d.getValue().getLaboratory().getName() : "";
            return new SimpleStringProperty(deptName + labName);
        });

        chambersCountCol.setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getChambers() != null ? d.getValue().getChambers().size() : 0)
        ));

        log.debug("Master table setup completed");
    }

    public void setupDetailTable(
            TableView<CoolingChamber> chambersDetailsTable,
            TableColumn<CoolingChamber, String> detChamberNameCol,
            TableColumn<CoolingChamber, String> detChamberTypeCol,
            TableColumn<CoolingChamber, String> detChamberRangeCol,
            TableColumn<CoolingChamber, String> detChamberVolumeCol,
            TableColumn<CoolingChamber, String> detChamberMaterialCol) {
        
        detChamberNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getChamberName()));
        detChamberTypeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getChamberType().getDisplayName()));
        
        detChamberRangeCol.setCellValueFactory(c -> {
            Double min = c.getValue().getMinOperatingTemp();
            Double max = c.getValue().getMaxOperatingTemp();
            if (min == null && max == null) return new SimpleStringProperty("–");
            return new SimpleStringProperty((min != null ? min : "–") + "°C do " + (max != null ? max : "–") + "°C");
        });
        
        detChamberVolumeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormattedVolume()));
        detChamberMaterialCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMaterialName()));

        log.debug("Detail table setup completed");
    }

    public void setupFilters(
            TextField searchField,
            ComboBox<String> chamberFilter,
            ObservableList<CoolingDevice> masterData,
            TableView<CoolingDevice> deviceTable) {
        
        ObservableList<String> types = FXCollections.observableArrayList("Wszystkie");
        for (ChamberType ct : ChamberType.values()) {
            types.add(ct.getDisplayName());
        }
        chamberFilter.setItems(types);
        chamberFilter.getSelectionModel().selectFirst();

        FilteredList<CoolingDevice> filteredData = new FilteredList<>(masterData, p -> true);
        searchField.textProperty().addListener((obs, old, newValue) -> applyFilters(searchField, chamberFilter, filteredData));
        chamberFilter.valueProperty().addListener((obs, old, newValue) -> applyFilters(searchField, chamberFilter, filteredData));

        deviceTable.setItems(filteredData);
        log.debug("Filters setup completed");
    }

    private void applyFilters(TextField searchField, ComboBox<String> chamberFilter, FilteredList<CoolingDevice> filteredData) {
        String query = searchField.getText().toLowerCase().trim();
        String selectedType = chamberFilter.getValue();

        filteredData.setPredicate(d -> {
            boolean matchesSearch = query.isEmpty() || 
                                    d.getInventoryNumber().toLowerCase().contains(query) || 
                                    d.getName().toLowerCase().contains(query);
            
            boolean matchesType = "Wszystkie".equals(selectedType) || 
                                  (d.getChambers() != null && d.getChambers().stream()
                                      .anyMatch(c -> c.getChamberType().getDisplayName().equals(selectedType)));

            return matchesSearch && matchesType;
        });
    }

    public FilteredList<CoolingDevice> createFilteredList(ObservableList<CoolingDevice> masterData) {
        return new FilteredList<>(masterData, p -> true);
    }
}
```

---

## Krok 2: CoolingDeviceCellFactoryService

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.VolumeCategory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import atlantafx.base.theme.Styles;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceCellFactoryService {

    private final TestoRevalidationService revalidationService;

    public void setupVolumeCategoryCell(TableColumn<CoolingChamber, String> detChamberPdaCol) {
        detChamberPdaCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "BRAK".equals(item)) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label();
                    tagLabel.getStyleClass().add("tag");
                    
                    try {
                        VolumeCategory cat = VolumeCategory.valueOf(item);
                        switch (cat) {
                            case SMALL -> {
                                tagLabel.setText("Klasa S (≤ 2 m³) / 9 pkt");
                                tagLabel.getStyleClass().add(Styles.SUCCESS);
                            }
                            case MEDIUM -> {
                                tagLabel.setText("Klasa M (2–20 m³) / 15 pkt");
                                tagLabel.getStyleClass().add(Styles.ACCENT);
                            }
                            case LARGE -> {
                                tagLabel.setText("Klasa L (> 20 m³) / 27 pkt");
                                tagLabel.getStyleClass().add(Styles.WARNING);
                            }
                        }
                    } catch (Exception e) {
                        tagLabel.setText(item);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }

    public void setupRevalidationStatusCell(TableColumn<CoolingChamber, String> detChamberRevalCol) {
        detChamberRevalCol.setCellValueFactory(c -> {
            String status = revalidationService.getRevalidationStatusText(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(status);
        });

        detChamberRevalCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label();
                    tagLabel.getStyleClass().add("tag");
                    if (item.startsWith("Ważna")) {
                        tagLabel.setText("✅ " + item);
                        tagLabel.getStyleClass().add(Styles.SUCCESS);
                    } else if (item.startsWith("Brak") || item.startsWith("Wymagana")) {
                        tagLabel.setText("❌ " + item);
                        tagLabel.getStyleClass().add(Styles.DANGER);
                    } else {
                        tagLabel.setText("⚠️ " + item);
                        tagLabel.getStyleClass().add(Styles.WARNING);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }
}
```

---

## Krok 3: CoolingDeviceSecurityService

```java
package com.mac.bry.desktop.service;

import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceSecurityService {

    public boolean isUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") || 
                             a.getAuthority().equals("ROLE_DEPT_ADMIN"));
    }

    public void configureAdminButtonVisibility(Button addDeviceButton) {
        addDeviceButton.setVisible(isUserAdmin());
        addDeviceButton.setManaged(isUserAdmin());
    }
}
```

---

## Krok 4: Refactored CoolingDeviceController

```java
package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.service.CoolingDeviceService;
import com.mac.bry.desktop.service.CoolingDeviceTableService;
import com.mac.bry.desktop.service.CoolingDeviceCellFactoryService;
import com.mac.bry.desktop.service.CoolingDeviceSecurityService;
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
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoolingDeviceController {

    private final CoolingDeviceService coolingDeviceService;
    private final CoolingDeviceTableService tableService;
    private final CoolingDeviceCellFactoryService cellFactoryService;
    private final CoolingDeviceSecurityService securityService;
    private final ApplicationContext applicationContext;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> chamberFilter;
    
    @FXML private TableView<CoolingDevice> deviceTable;
    @FXML private TableColumn<CoolingDevice, String> inventoryCol, nameCol, deptCol, chambersCountCol;
    @FXML private TableColumn<CoolingDevice, Void> actionsCol;

    @FXML private Label chambersTitleLabel;
    @FXML private TableView<CoolingChamber> chambersDetailsTable;
    @FXML private TableColumn<CoolingChamber, String> detChamberNameCol, detChamberTypeCol, 
                                                      detChamberRangeCol, detChamberVolumeCol, 
                                                      detChamberPdaCol, detChamberRevalCol, 
                                                      detChamberMaterialCol;

    @FXML private Label deviceCountLabel;
    @FXML private Button addDeviceButton;

    private final ObservableList<CoolingDevice> masterData = FXCollections.observableArrayList();
    private boolean isAdmin = false;

    @FXML
    public void initialize() {
        isAdmin = securityService.isUserAdmin();
        securityService.configureAdminButtonVisibility(addDeviceButton);
        
        tableService.setupMasterTable(deviceTable, inventoryCol, nameCol, deptCol, chambersCountCol);
        tableService.setupDetailTable(chambersDetailsTable, detChamberNameCol, detChamberTypeCol, 
                                      detChamberRangeCol, detChamberVolumeCol, detChamberMaterialCol);
        
        cellFactoryService.setupVolumeCategoryCell(detChamberPdaCol);
        cellFactoryService.setupRevalidationStatusCell(detChamberRevalCol);
        
        setupActionsColumn();
        tableService.setupFilters(searchField, chamberFilter, masterData, deviceTable);
        setupSelectionListener();
        
        handleRefresh();
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
                chambersTitleLabel.setText("Komory urządzenia: " + newVal.getName() + " (" + newVal.getInventoryNumber() + ")");
                if (newVal.getChambers() != null) {
                    chambersDetailsTable.setItems(FXCollections.observableArrayList(newVal.getChambers()));
                } else {
                    chambersDetailsTable.setItems(FXCollections.emptyObservableList());
                }
            } else {
                chambersTitleLabel.setText("Komory wybranego urządzenia");
                chambersDetailsTable.setItems(FXCollections.emptyObservableList());
            }
        });
    }

    @FXML
    public void handleRefresh() {
        List<CoolingDevice> devices = coolingDeviceService.findAll();
        masterData.setAll(devices);
        deviceCountLabel.setText("Znaleziono: " + masterData.size() + " urządzeń");
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
                    log.error("Error deleting device", ex);
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/user_audit.fxml"));
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
            log.error("Error opening cooling chamber audit", e);
        }
    }

    private void openDeviceDialog(CoolingDevice device, boolean isEdit, boolean readOnly) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/cooling_device_dialog.fxml"));
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
            log.error("Error opening device dialog", e);
        }
    }
}
```

---

## Podsumowanie Zmian

| Aspekt | Przed | Po | Poprawa |
|--------|-------|----|----|
| **Rozmiar kontrolera** | 378 linii | ~240 linii | -36% |
| **Liczba serwisów** | 2 | 5 | +150% modularność |
| **Odpowiedzialności** | 8+ | 2-3 | Lepszy SRP |
| **Cell factory logika** | W kontrolerze | W serwisie | Łatwiejsze testowanie |
| **Table setup logika** | W kontrolerze | W serwisie | Lepsze oddzielenie |

---

## Checklistę Implementacji

- [ ] Stworzyć CoolingDeviceTableService
- [ ] Stworzyć CoolingDeviceCellFactoryService
- [ ] Stworzyć CoolingDeviceSecurityService
- [ ] Refactoryzować CoolingDeviceController
- [ ] Uruchomić testy integracyjne
- [ ] Zweryfikować UI w aplikacji

