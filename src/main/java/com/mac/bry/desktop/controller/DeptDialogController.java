package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.Department;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class DeptDialogController {

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField abbrField;
    @FXML private TextArea descField;

    @Getter
    private boolean saved = false;
    private Department department;

    public void setDepartment(Department dept, boolean isEdit) {
        this.department = dept;
        titleLabel.setText(isEdit ? "Edytuj Dział" : "Nowy Dział");
        if (dept != null) {
            nameField.setText(dept.getName());
            abbrField.setText(dept.getAbbreviation());
            descField.setText(dept.getDescription());
        }
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        String abbr = abbrField.getText() != null ? abbrField.getText().trim() : "";
        String desc = descField.getText() != null ? descField.getText().trim() : "";

        if (name.isEmpty()) {
            return;
        }
        
        department.setName(name);
        department.setAbbreviation(abbr);
        department.setDescription(desc);
        
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
