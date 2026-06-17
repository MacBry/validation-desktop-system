package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.repository.ThermoRecorderModelRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class ThermoRecorderModelDialogController {

    private final ThermoRecorderModelRepository modelRepository;

    @FXML private TextField nameField;
    @FXML private Spinner<Integer> channelsSpinner;
    @FXML private TextField resolutionField;
    @FXML private CheckBox activeCheckBox;

    private ThermoRecorderModel model;
    private boolean saved = false;

    @FXML
    public void initialize() {
        SpinnerValueFactory<Integer> factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 16, 1);
        channelsSpinner.setValueFactory(factory);
    }

    public void initData(ThermoRecorderModel model, boolean isEdit) {
        this.model = model;
        if (isEdit) {
            nameField.setText(model.getName());
            channelsSpinner.getValueFactory().setValue(model.getChannelCount());
            resolutionField.setText(model.getDefaultResolution() != null ? model.getDefaultResolution().toString() : "0.1");
            activeCheckBox.setSelected(model.getActive() != null ? model.getActive() : false);
        } else {
            activeCheckBox.setSelected(true);
            resolutionField.setText("0.100");
        }
    }

    @FXML
    public void handleSave() {
        try {
            if (nameField.getText().trim().isEmpty()) {
                showError("Nazwa modelu jest wymagana");
                return;
            }

            model.setName(nameField.getText().trim());
            model.setChannelCount(channelsSpinner.getValue());
            try {
                model.setDefaultResolution(new BigDecimal(resolutionField.getText().trim().replace(",", ".")));
            } catch (NumberFormatException ex) {
                showError("Nieprawidłowy format rozdzielczości");
                return;
            }
            model.setActive(activeCheckBox.isSelected());

            modelRepository.save(model);
            saved = true;
            close();
        } catch (Exception e) {
            log.error("Błąd zapisu modelu", e);
            showError("Błąd zapisu: " + e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        close();
    }

    private void close() {
        ((Stage) nameField.getScene().getWindow()).close();
    }

    public boolean isSaved() {
        return saved;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Błąd walidacji");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
