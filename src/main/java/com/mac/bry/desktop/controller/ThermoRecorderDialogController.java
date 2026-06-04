package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.service.ThermoRecorderService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
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
public class ThermoRecorderDialogController {

    private final ThermoRecorderService recorderService;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;

    @FXML private TextField snField;
    @FXML private TextField modelField;
    @FXML private ComboBox<RecorderStatus> statusComboBox;
    @FXML private TextField resolutionField;
    @FXML private ComboBox<Department> deptComboBox;
    @FXML private ComboBox<Laboratory> labComboBox;

    private ThermoRecorder recorder;
    private boolean saved = false;

    @FXML
    public void initialize() {
        setupComboBoxes();
    }

    private void setupComboBoxes() {
        statusComboBox.setItems(FXCollections.observableArrayList(RecorderStatus.values()));
        statusComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(RecorderStatus status) { return status == null ? "" : status.getDisplayName(); }
            @Override public RecorderStatus fromString(String s) { return null; }
        });

        deptComboBox.setItems(FXCollections.observableArrayList(departmentRepository.findAll()));
        deptComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });

        labComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(Laboratory l) { return l == null ? "" : l.getName(); }
            @Override public Laboratory fromString(String s) { return null; }
        });

        deptComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                labComboBox.setItems(FXCollections.observableArrayList(laboratoryRepository.findByDepartmentId(newVal.getId())));
            } else {
                labComboBox.setItems(FXCollections.emptyObservableList());
            }
        });
    }

    public void setRecorder(ThermoRecorder recorder, boolean isEdit) {
        this.recorder = recorder;
        if (isEdit) {
            snField.setText(recorder.getSerialNumber());
            modelField.setText(recorder.getModel());
            statusComboBox.getSelectionModel().select(recorder.getStatus());
            resolutionField.setText(recorder.getResolution().toString());
            deptComboBox.getSelectionModel().select(recorder.getDepartment());
            labComboBox.getSelectionModel().select(recorder.getLaboratory());
        } else {
            statusComboBox.getSelectionModel().select(RecorderStatus.ACTIVE);
            resolutionField.setText("0.1");
        }
    }

    @FXML
    public void handleSave() {
        try {
            recorder.setSerialNumber(snField.getText());
            recorder.setModel(modelField.getText());
            recorder.setStatus(statusComboBox.getValue());
            recorder.setResolution(new BigDecimal(resolutionField.getText()));
            recorder.setDepartment(deptComboBox.getValue());
            recorder.setLaboratory(labComboBox.getValue());

            if (recorder.getDepartment() == null) {
                log.error("Dział jest wymagany");
                return;
            }

            recorderService.saveRecorder(recorder);
            saved = true;
            close();
        } catch (Exception e) {
            log.error("Błąd podczas zapisu rejestratora", e);
        }
    }

    @FXML
    public void handleCancel() {
        close();
    }

    private void close() {
        ((Stage) snField.getScene().getWindow()).close();
    }

    public boolean isSaved() {
        return saved;
    }
}
