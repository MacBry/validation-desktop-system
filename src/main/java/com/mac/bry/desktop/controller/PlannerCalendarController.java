package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.model.PlannedValidationTask;
import com.mac.bry.desktop.model.TaskResourceStatus;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.service.planner.RevalidationSchedulerEngine;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Kalendarz planera — zadania walidacyjne roku wraz ze stanem obsady
 * rejestratorów.
 * <p>
 * Zadania bez kompletu sprzętu są wyróżnione, a nie ukryte: plan ma pokazywać
 * luki, bo to one wymagają decyzji (dołożenia sprzętu, wysłania go na
 * wzorcowanie albo przesunięcia terminu).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerCalendarController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PlannedValidationTaskRepository taskRepository;
    private final RevalidationSchedulerEngine schedulerEngine;

    @FXML private ComboBox<Integer> yearCombo;
    @FXML private CheckBox onlyProblemsCheck;
    @FXML private Label lblSummary;
    @FXML private Label lblShortageDetail;
    @FXML private TableView<PlannedValidationTask> taskTable;
    @FXML private TableColumn<PlannedValidationTask, String> colDueDate;
    @FXML private TableColumn<PlannedValidationTask, String> colTaskNumber;
    @FXML private TableColumn<PlannedValidationTask, String> colDevice;
    @FXML private TableColumn<PlannedValidationTask, String> colChamber;
    @FXML private TableColumn<PlannedValidationTask, String> colProcedure;
    @FXML private TableColumn<PlannedValidationTask, String> colStep1;
    @FXML private TableColumn<PlannedValidationTask, String> colMeasurementEnd;
    @FXML private TableColumn<PlannedValidationTask, String> colReadoutDeadline;
    @FXML private TableColumn<PlannedValidationTask, String> colRecorders;
    @FXML private TableColumn<PlannedValidationTask, String> colStatus;
    @FXML private TableColumn<PlannedValidationTask, String> colResourceStatus;

    @FXML
    public void initialize() {
        setupYearCombo();
        setupTable();
        refresh();
    }

    private void setupYearCombo() {
        int currentYear = LocalDate.now().getYear();
        yearCombo.setItems(FXCollections.observableArrayList(
                List.of(currentYear - 1, currentYear, currentYear + 1)));
        yearCombo.getSelectionModel().select(Integer.valueOf(currentYear));
        yearCombo.valueProperty().addListener((obs, old, value) -> refresh());
        onlyProblemsCheck.selectedProperty().addListener((obs, old, value) -> refresh());
    }

    private void setupTable() {
        colDueDate.setCellValueFactory(c -> text(c.getValue().getDueDate() != null
                ? c.getValue().getDueDate().format(DATE_FMT) : "–"));
        colTaskNumber.setCellValueFactory(c -> text(c.getValue().getTaskNumber()));
        colDevice.setCellValueFactory(c -> text(deviceName(c.getValue())));
        colChamber.setCellValueFactory(c -> text(c.getValue().getCoolingChamber() != null
                ? c.getValue().getCoolingChamber().getChamberName() : "–"));
        colProcedure.setCellValueFactory(c -> text(c.getValue().getProcedureType() != null
                ? c.getValue().getProcedureType().getDisplayName() : "–"));
        colStep1.setCellValueFactory(c -> text(formatDateTime(c.getValue().getPlannedStep1Time())));
        colMeasurementEnd.setCellValueFactory(c -> text(formatDateTime(c.getValue().getPlannedStep4MapEnd())));
        colReadoutDeadline.setCellValueFactory(
                c -> text(formatDateTime(c.getValue().getPlannedStep5ReadoutDeadline())));
        colRecorders.setCellValueFactory(c -> text(String.valueOf(c.getValue().getRequiredRecorderCount())));
        colStatus.setCellValueFactory(c -> text(c.getValue().getStatus() != null
                ? c.getValue().getStatus().getDisplayName() : "–"));
        colResourceStatus.setCellValueFactory(c -> text(c.getValue().getResourceStatus() != null
                ? c.getValue().getResourceStatus().getDisplayName() : "–"));

        highlightProblemRows();

        taskTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, task) -> showShortageDetail(task));
    }

    /**
     * Zadania bez obsady i przeterminowane muszą rzucać się w oczy — to one
     * wymagają reakcji planisty.
     */
    private void highlightProblemRows() {
        taskTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(PlannedValidationTask task, boolean empty) {
                super.updateItem(task, empty);
                getStyleClass().removeAll("planner-row-blocked", "planner-row-overdue");
                if (empty || task == null) {
                    return;
                }
                if (task.isBlockedByResources()) {
                    getStyleClass().add("planner-row-blocked");
                } else if (task.isOverdue()) {
                    getStyleClass().add("planner-row-overdue");
                }
            }
        });
    }

    private void showShortageDetail(PlannedValidationTask task) {
        if (task == null || !task.isBlockedByResources()) {
            lblShortageDetail.setText("");
            lblShortageDetail.setVisible(false);
            lblShortageDetail.setManaged(false);
            return;
        }
        StringBuilder detail = new StringBuilder(task.getShortageReason() != null
                ? task.getShortageReason() : task.getResourceStatus().getDisplayName());
        if (task.getSuggestedWindowStart() != null) {
            detail.append("\n").append(I18n.t("planner.calendar.suggested_window"))
                  .append(" ").append(formatDateTime(task.getSuggestedWindowStart()));
        }
        lblShortageDetail.setText(detail.toString());
        lblShortageDetail.setVisible(true);
        lblShortageDetail.setManaged(true);
    }

    @FXML
    public void onGenerateSchedule() {
        Integer year = yearCombo.getValue();
        if (year == null) {
            return;
        }
        try {
            List<PlannedValidationTask> generated = schedulerEngine.generateYearlySchedule(year);
            refresh();

            long blocked = generated.stream().filter(PlannedValidationTask::isBlockedByResources).count();
            String message = generated.isEmpty()
                    ? I18n.t("planner.calendar.generated_none")
                    : I18n.t("planner.calendar.generated_summary", generated.size(), blocked);
            showInfo(message);
        } catch (Exception e) {
            log.error("Nie udało się wygenerować planu rocznego dla roku {}", year, e);
            showError(I18n.t("planner.calendar.generate_failed") + "\n\n" + e.getMessage());
        }
    }

    @FXML
    public void onRefresh() {
        refresh();
    }

    private void refresh() {
        Integer year = yearCombo.getValue();
        if (year == null) {
            return;
        }
        List<PlannedValidationTask> tasks = taskRepository.findByDueDateRangeWithDetails(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        List<PlannedValidationTask> visible = onlyProblemsCheck.isSelected()
                ? tasks.stream().filter(PlannedValidationTask::isBlockedByResources).toList()
                : tasks;

        taskTable.setItems(FXCollections.observableArrayList(visible));

        long blocked = tasks.stream().filter(PlannedValidationTask::isBlockedByResources).count();
        long overdue = tasks.stream().filter(PlannedValidationTask::isOverdue).count();
        lblSummary.setText(I18n.t("planner.calendar.summary", tasks.size(), blocked, overdue));
        showShortageDetail(null);
    }

    private String deviceName(PlannedValidationTask task) {
        if (task.getCoolingChamber() == null || task.getCoolingChamber().getCoolingDevice() == null) {
            return "–";
        }
        var device = task.getCoolingChamber().getCoolingDevice();
        return device.getName() + " (" + device.getInventoryNumber() + ")";
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        return value != null ? value.format(DATE_TIME_FMT) : "–";
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value != null ? value : "–");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("planner.calendar.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("planner.calendar.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Wyłącznie do testów jednostkowych stanu tabeli. */
    TaskResourceStatus selectedResourceStatus() {
        PlannedValidationTask selected = taskTable.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getResourceStatus() : null;
    }
}