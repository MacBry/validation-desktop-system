package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.service.ThermoRecorderService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
public class ThermoRecorderController {

    private final ThermoRecorderService recorderService;
    private final ApplicationContext applicationContext;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<ThermoRecorder> recorderTable;
    @FXML private TableColumn<ThermoRecorder, String> snColumn;
    @FXML private TableColumn<ThermoRecorder, String> modelColumn;
    @FXML private TableColumn<ThermoRecorder, String> deptColumn;
    @FXML private TableColumn<ThermoRecorder, String> statusColumn;
    @FXML private TableColumn<ThermoRecorder, String> calibrationColumn;
    @FXML private TableColumn<ThermoRecorder, Void> actionsColumn;
    @FXML private Label recordCountLabel;

    private final ObservableList<ThermoRecorder> masterData = FXCollections.observableArrayList();
    private FilteredList<ThermoRecorder> filteredData;

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        handleRefresh();
    }

    private void setupTable() {
        com.mac.bry.desktop.controller.helper.ThermoRecorderTableHelper.setupTable(snColumn, modelColumn, deptColumn);
        com.mac.bry.desktop.controller.helper.ThermoRecorderCellFactoryHelper.setupStatusColumn(statusColumn);
        com.mac.bry.desktop.controller.helper.ThermoRecorderCellFactoryHelper.setupCalibrationColumn(calibrationColumn, recorderService::getCalibrationStatus);
        com.mac.bry.desktop.controller.helper.ThermoRecorderCellFactoryHelper.setupActionsColumn(actionsColumn, this::handleEditRecorder, this::handleShowCalibrationHistory, this::handleShowAudit);
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList("Wszystkie", "Aktywne", "Nieaktywne", "Wzorcowanie", "Wyłączone"));
        statusFilter.getSelectionModel().selectFirst();

        filteredData = new FilteredList<>(masterData, p -> true);
        com.mac.bry.desktop.controller.helper.ThermoRecorderTableHelper.setupFilters(searchField, statusFilter, filteredData);

        searchField.textProperty().addListener((obs, old, newValue) -> updateCountLabel());
        statusFilter.valueProperty().addListener((obs, old, newValue) -> updateCountLabel());

        recorderTable.setItems(filteredData);
    }

    private void updateCountLabel() {
        recordCountLabel.setText("Znaleziono: " + filteredData.size() + " rejestratorów");
    }

    @FXML
    public void handleRefresh() {
        List<ThermoRecorder> recorders = recorderService.getAllRecorders();
        masterData.setAll(recorders);
        recordCountLabel.setText("Znaleziono: " + masterData.size() + " rejestratorów");
    }

    @FXML
    public void handleAddNewRecorder() {
        openRecorderDialog(new ThermoRecorder(), false);
    }

    private void handleEditRecorder(ThermoRecorder recorder) {
        openRecorderDialog(recorder, true);
    }

    private void handleShowCalibrationHistory(ThermoRecorder recorder) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/calibration_history.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            CalibrationHistoryController controller = loader.getController();
            controller.setRecorder(recorder);

            Stage stage = new Stage();
            stage.setTitle("Historia Wzorcowań: " + recorder.getSerialNumber());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            handleRefresh();
        } catch (IOException e) {
            log.error("Błąd podczas otwierania historii wzorcowań", e);
        }
    }

    private void handleShowAudit(ThermoRecorder recorder) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/user_audit.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            UserAuditController controller = loader.getController();
            controller.initRecorderData(recorder);

            Stage stage = new Stage();
            stage.setTitle("Audit Trail: " + recorder.getSerialNumber());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            log.error("Błąd podczas otwierania audytu rejestratora", e);
        }
    }

    private void openRecorderDialog(ThermoRecorder recorder, boolean isEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/thermo_recorder_dialog.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            ThermoRecorderDialogController controller = loader.getController();
            controller.setRecorder(recorder, isEdit);

            Stage stage = new Stage();
            stage.setTitle(isEdit ? "Edycja Rejestratora" : "Nowy Rejestrator");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                handleRefresh();
            }
        } catch (IOException e) {
            log.error("Błąd podczas otwierania okna rejestratora", e);
        }
    }
}
