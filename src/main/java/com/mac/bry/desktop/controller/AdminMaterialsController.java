package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.service.MaterialTypeService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Kontroler zakładki "Słownik Materiałów" w panelu administracyjnym.
 * Wydzielony z AdminPanelController w celu zgodności z zasadą SRP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminMaterialsController {

    private final MaterialTypeService materialTypeService;
    private final ApplicationContext applicationContext;

    @FXML private TableView<MaterialType> materialsTable;
    @FXML private TableColumn<MaterialType, String> materialNameCol;
    @FXML private TableColumn<MaterialType, String> materialTempCol;
    @FXML private TableColumn<MaterialType, String> materialEaCol;
    @FXML private TableColumn<MaterialType, String> materialSourceCol;
    @FXML private TableColumn<MaterialType, Boolean> materialRequiresMappingCol;
    @FXML private TableColumn<MaterialType, Boolean> materialActiveCol;

    @FXML private Label selectedMaterialLabel;
    @FXML private TextField materialNameField;
    @FXML private TextField materialDescField;
    @FXML private TextField materialTempMinField;
    @FXML private TextField materialTempMaxField;
    @FXML private TextField materialEaField;
    @FXML private TextField materialSourceField;
    @FXML private CheckBox materialRequiresMappingCheckbox;
    @FXML private CheckBox materialActiveCheckbox;
    @FXML private Label materialStatusLabel;

    private MaterialType selectedMaterial;
    private final ObservableList<MaterialType> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        materialNameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        materialTempCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTemperatureRange()));
        materialEaCol.setCellValueFactory(c -> {
            BigDecimal ea = c.getValue().getActivationEnergy();
            return new SimpleStringProperty(ea != null ? String.format("%.2f", ea) : "–");
        });
        materialSourceCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("standardSource"));
        materialRequiresMappingCol.setCellValueFactory(c ->
                new SimpleBooleanProperty(c.getValue().getRequiresMapping() != null ? c.getValue().getRequiresMapping() : false));
        materialRequiresMappingCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label tag = new Label(item ? "Tak" : "Nie");
                tag.getStyleClass().add("tag");
                tag.getStyleClass().add(item
                        ? atlantafx.base.theme.Styles.DANGER
                        : atlantafx.base.theme.Styles.SUCCESS);
                setGraphic(tag);
                setText(null);
            }
        });
        materialActiveCol.setCellValueFactory(c ->
                new SimpleBooleanProperty(c.getValue().getActive() != null ? c.getValue().getActive() : false));
        materialActiveCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label tag = new Label(item ? "Aktywny" : "Dezaktywowany");
                tag.getStyleClass().add("tag");
                tag.getStyleClass().add(item
                        ? atlantafx.base.theme.Styles.SUCCESS
                        : atlantafx.base.theme.Styles.DANGER);
                setGraphic(tag);
                setText(null);
            }
        });

        materialsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            if (nv != null) showMaterialDetails(nv);
        });
        materialsTable.setItems(masterData);
        refreshMaterials();
    }

    private void showMaterialDetails(MaterialType material) {
        selectedMaterial = material;
        if (material == null) {
            selectedMaterialLabel.setText("Wybierz materiał z listy lub dodaj nowy");
            materialNameField.clear();
            materialDescField.clear();
            materialTempMinField.clear();
            materialTempMaxField.clear();
            materialEaField.clear();
            materialSourceField.clear();
            materialRequiresMappingCheckbox.setSelected(false);
            materialActiveCheckbox.setSelected(true);
            materialStatusLabel.setText("");
            return;
        }
        selectedMaterialLabel.setText("Edycja: " + material.getName());
        materialNameField.setText(material.getName());
        materialDescField.setText(material.getDescription() != null ? material.getDescription() : "");
        materialTempMinField.setText(material.getMinStorageTemp() != null ? String.valueOf(material.getMinStorageTemp()) : "");
        materialTempMaxField.setText(material.getMaxStorageTemp() != null ? String.valueOf(material.getMaxStorageTemp()) : "");
        materialEaField.setText(material.getActivationEnergy() != null ? String.valueOf(material.getActivationEnergy()) : "");
        materialSourceField.setText(material.getStandardSource() != null ? material.getStandardSource() : "");
        materialRequiresMappingCheckbox.setSelected(material.getRequiresMapping() != null ? material.getRequiresMapping() : false);
        materialActiveCheckbox.setSelected(material.getActive() != null ? material.getActive() : false);
        materialStatusLabel.setText("");
    }

    @FXML
    private void handleNewMaterial(ActionEvent event) {
        materialsTable.getSelectionModel().clearSelection();
        showMaterialDetails(null);
        selectedMaterialLabel.setText("Nowy Materiał Słownikowy");
    }

    @FXML
    private void handleSaveMaterial(ActionEvent event) {
        if (materialNameField.getText().trim().isEmpty()) {
            showError("Błąd walidacji", "Nazwa materiału jest wymagana.");
            return;
        }
        MaterialType mat = selectedMaterial != null ? selectedMaterial : new MaterialType();
        mat.setName(materialNameField.getText().trim());
        mat.setDescription(materialDescField.getText().trim().isEmpty() ? null : materialDescField.getText().trim());
        try {
            mat.setMinStorageTemp(materialTempMinField.getText().trim().isEmpty() ? null : Double.parseDouble(materialTempMinField.getText().trim()));
            mat.setMaxStorageTemp(materialTempMaxField.getText().trim().isEmpty() ? null : Double.parseDouble(materialTempMaxField.getText().trim()));
            mat.setActivationEnergy(materialEaField.getText().trim().isEmpty() ? null : new BigDecimal(materialEaField.getText().trim()));
        } catch (NumberFormatException e) {
            showError("Błąd formatu", "Zakresy temperatur oraz energia aktywacji muszą być liczbami!");
            return;
        }
        mat.setStandardSource(materialSourceField.getText().trim().isEmpty() ? null : materialSourceField.getText().trim());
        mat.setRequiresMapping(materialRequiresMappingCheckbox.isSelected());
        mat.setActive(materialActiveCheckbox.isSelected());
        try {
            materialTypeService.save(mat);
            refreshMaterials();
            materialStatusLabel.setText("Pomyślnie zapisano materiał.");
            showMaterialDetails(null);
        } catch (Exception ex) {
            log.error("Nie udało się zapisać materiału", ex);
            showError("Błąd bazy danych", "Nie udało się zapisać materiału: " + ex.getMessage());
        }
    }

    @FXML
    private void handleMaterialAudit(ActionEvent event) {
        MaterialType sel = materialsTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Brak zaznaczenia", "Wybierz materiał z listy."); return; }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/user_audit.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            UserAuditController ctrl = loader.getController();
            ctrl.initMaterialData(sel);
            Stage stage = new Stage();
            stage.setTitle("Audit Trail: " + sel.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            log.error("Błąd podczas otwierania audytu materiału", e);
            showError("Błąd", "Nie udało się wyświetlić okna audytu: " + e.getMessage());
        }
    }

    private void refreshMaterials() {
        masterData.setAll(materialTypeService.findAll());
    }

    private void showError(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
