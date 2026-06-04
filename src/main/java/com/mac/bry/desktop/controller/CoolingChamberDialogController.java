package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.ChamberType;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.model.VolumeCategory;
import com.mac.bry.desktop.service.MaterialTypeService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoolingChamberDialogController {

    private final MaterialTypeService materialTypeService;

    @FXML private Label dialogTitleLabel;
    @FXML private Label dialogSubtitleLabel;
    @FXML private TextField chamberNameField;
    @FXML private ComboBox<ChamberType> chamberTypeCombo;
    @FXML private ComboBox<MaterialType> materialTypeCombo;
    @FXML private TextField tempMinField;
    @FXML private TextField tempMaxField;
    @FXML private TextField volumeField;
    @FXML private Label volumeCategoryBadge;
    @FXML private Label pdaCategoryLabel;
    @FXML private Label pdaSensorLabel;
    @FXML private Button saveButton;

    private CoolingChamber coolingChamber;
    private boolean isEdit = false;
    private boolean readOnly = false;
    private boolean saved = false;

    @FXML
    public void initialize() {
        setupConverters();
        setupListeners();
    }

    public void setChamber(CoolingChamber chamber, boolean isEdit, boolean readOnly) {
        this.coolingChamber = chamber;
        this.isEdit = isEdit;
        this.readOnly = readOnly;

        populateCombos();

        if (readOnly) {
            dialogTitleLabel.setText("Szczegóły Komory");
            dialogSubtitleLabel.setText("Widok parametrów komory w trybie tylko do odczytu.");
            disableAllFields();
        } else if (isEdit) {
            dialogTitleLabel.setText("Edycja Komory");
            dialogSubtitleLabel.setText("Modyfikujesz parametry techniczne komory.");
        } else {
            dialogTitleLabel.setText("Nowa Komora");
            dialogSubtitleLabel.setText("Dodajesz nową komorę chłodniczą/mroźniczą do urządzenia.");
        }

        if (chamber != null) {
            chamberNameField.setText(chamber.getChamberName() != null ? chamber.getChamberName() : "");
            chamberTypeCombo.setValue(chamber.getChamberType());
            materialTypeCombo.setValue(chamber.getMaterialType());
            tempMinField.setText(chamber.getMinOperatingTemp() != null ? String.valueOf(chamber.getMinOperatingTemp()) : "");
            tempMaxField.setText(chamber.getMaxOperatingTemp() != null ? String.valueOf(chamber.getMaxOperatingTemp()) : "");
            volumeField.setText(chamber.getVolume() != null ? String.valueOf(chamber.getVolume()) : "");
            
            updatePdaDetails(chamber.getVolume());
        }
    }

    public boolean isSaved() {
        return saved;
    }

    private void setupConverters() {
        // Konwerter dla Typu Komory
        chamberTypeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ChamberType object) {
                return object != null ? object.getDisplayName() : "";
            }

            @Override
            public ChamberType fromString(String string) {
                return null;
            }
        });

        // Konwerter dla Materiału
        materialTypeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(MaterialType object) {
                return object != null ? object.getName() + " (" + object.getTemperatureRange() + ")" : "";
            }

            @Override
            public MaterialType fromString(String string) {
                return null;
            }
        });
    }

    private void setupListeners() {
        // Zmiana pojemności w czasie rzeczywistym przelicza wymagania PDA TR-64
        volumeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                clearPdaDetails();
                return;
            }
            try {
                double vol = Double.parseDouble(newVal.trim());
                updatePdaDetails(vol);
            } catch (NumberFormatException e) {
                clearPdaDetails();
            }
        });
    }

    private void populateCombos() {
        chamberTypeCombo.setItems(FXCollections.observableArrayList(ChamberType.values()));
        materialTypeCombo.setItems(FXCollections.observableArrayList(materialTypeService.findAllActive()));
    }

    private void updatePdaDetails(Double vol) {
        if (vol == null || vol <= 0) {
            clearPdaDetails();
            return;
        }

        VolumeCategory cat = VolumeCategory.fromVolume(vol);
        switch (cat) {
            case SMALL -> {
                volumeCategoryBadge.setText("KLASA S");
                volumeCategoryBadge.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 15px; -fx-background-radius: 6px;");
                pdaCategoryLabel.setText("Klasa S – Szafy chłodnicze, lodówki i zamrażarki (≤ 2 m³)");
                pdaSensorLabel.setText("Minimum 9 punktów pomiarowych (narożniki + środek przestrzeni roboczej)");
            }
            case MEDIUM -> {
                volumeCategoryBadge.setText("KLASA M");
                volumeCategoryBadge.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 15px; -fx-background-radius: 6px;");
                pdaCategoryLabel.setText("Klasa M – Szafy chłodnicze walk-in i komory (2 – 20 m³)");
                pdaSensorLabel.setText("Minimum 15 punktów pomiarowych (narożniki, środek i ściany boczne)");
            }
            case LARGE -> {
                volumeCategoryBadge.setText("KLASA L");
                volumeCategoryBadge.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 15px; -fx-background-radius: 6px;");
                pdaCategoryLabel.setText("Klasa L – Komory chłodnicze / mroźnie (> 20 m³)");
                pdaSensorLabel.setText("Minimum 27 punktów pomiarowych (pełna siatka 3D mapowania)");
            }
        }
    }

    private void clearPdaDetails() {
        volumeCategoryBadge.setText("BRAK DANYCH");
        volumeCategoryBadge.setStyle("-fx-background-color: #cbd5e1; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 8px 15px; -fx-background-radius: 6px;");
        pdaCategoryLabel.setText("Wprowadź pojemność, aby zobaczyć sugerowaną klasę.");
        pdaSensorLabel.setText("Brak wytycznych czujników.");
    }

    private void disableAllFields() {
        chamberNameField.setDisable(true);
        chamberTypeCombo.setDisable(true);
        materialTypeCombo.setDisable(true);
        tempMinField.setDisable(true);
        tempMaxField.setDisable(true);
        volumeField.setDisable(true);
        saveButton.setVisible(false);
        saveButton.setManaged(false);
    }

    @FXML
    private void handleSave() {
        if (readOnly) return;

        if (validateFields()) {
            coolingChamber.setChamberName(chamberNameField.getText().trim());
            coolingChamber.setChamberType(chamberTypeCombo.getValue());
            coolingChamber.setMaterialType(materialTypeCombo.getValue());

            try {
                if (!tempMinField.getText().trim().isEmpty()) {
                    coolingChamber.setMinOperatingTemp(Double.parseDouble(tempMinField.getText().trim()));
                } else {
                    coolingChamber.setMinOperatingTemp(null);
                }
                
                if (!tempMaxField.getText().trim().isEmpty()) {
                    coolingChamber.setMaxOperatingTemp(Double.parseDouble(tempMaxField.getText().trim()));
                } else {
                    coolingChamber.setMaxOperatingTemp(null);
                }
                
                if (!volumeField.getText().trim().isEmpty()) {
                    double vol = Double.parseDouble(volumeField.getText().trim());
                    coolingChamber.setVolume(vol);
                    coolingChamber.updateVolumeCategoryFromVolume();
                } else {
                    coolingChamber.setVolume(null);
                    coolingChamber.setVolumeCategory(null);
                }

                saved = true;
                handleCancel();

            } catch (NumberFormatException ex) {
                showError("Błąd formatu", "Objętość oraz zakresy temperatur muszą być poprawacznymi liczbami!");
            }
        }
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) chamberNameField.getScene().getWindow();
        stage.close();
    }

    private boolean validateFields() {
        StringBuilder errorMsg = new StringBuilder();

        if (chamberNameField.getText().trim().isEmpty()) {
            errorMsg.append("- Nazwa komory jest wymagana.\n");
        }
        if (chamberTypeCombo.getValue() == null) {
            errorMsg.append("- Wybór typu komory jest wymagany.\n");
        }

        // Walidacja zakresów temperatur
        if (!tempMinField.getText().trim().isEmpty() && !tempMaxField.getText().trim().isEmpty()) {
            try {
                double min = Double.parseDouble(tempMinField.getText().trim());
                double max = Double.parseDouble(tempMaxField.getText().trim());
                if (min >= max) {
                    errorMsg.append("- Temperatura minimalna musi być niższa niż maksymalna.\n");
                }
            } catch (NumberFormatException e) {
                errorMsg.append("- Temperatura musi być liczbą.\n");
            }
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
