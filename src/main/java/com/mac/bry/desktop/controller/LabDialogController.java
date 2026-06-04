package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.Laboratory;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class LabDialogController {

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField abbrField;

    @Getter
    private boolean saved = false;
    private Laboratory laboratory;

    public void setLaboratory(Laboratory lab, boolean isEdit) {
        this.laboratory = lab;
        titleLabel.setText(isEdit ? "Edytuj Pracownię" : "Nowa Pracownia");
        if (lab != null) {
            nameField.setText(lab.getName());
            abbrField.setText(lab.getAbbreviation());
        }
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        String abbr = abbrField.getText() != null ? abbrField.getText().trim() : "";

        if (name.isEmpty()) {
            return;
        }
        
        laboratory.setName(name);
        laboratory.setAbbreviation(abbr);
        
        saved = true;
        closeStage();
    }

    @FXML
    private void handleCancel() {
        saved = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }
}
