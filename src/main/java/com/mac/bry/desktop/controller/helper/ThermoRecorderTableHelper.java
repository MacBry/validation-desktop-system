package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThermoRecorderTableHelper {

    public static void setupTable(
            TableColumn<ThermoRecorder, String> snColumn,
            TableColumn<ThermoRecorder, String> modelColumn,
            TableColumn<ThermoRecorder, String> deptColumn) {

        snColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSerialNumber()));
        modelColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getModel() != null ? d.getValue().getModel().getName() : ""));
        deptColumn.setCellValueFactory(d -> {
            String deptName = d.getValue().getDepartment() != null ? d.getValue().getDepartment().getName() : "-";
            String labName = d.getValue().getLaboratory() != null ? " / " + d.getValue().getLaboratory().getName() : "";
            return new SimpleStringProperty(deptName + labName);
        });

        log.debug("Thermo recorder table columns setup completed");
    }

    public static void setupFilters(
            TextField searchField,
            ComboBox<String> statusFilter,
            FilteredList<ThermoRecorder> filteredData) {

        searchField.textProperty().addListener((obs, old, newValue) -> applyFilters(searchField, statusFilter, filteredData));
        statusFilter.valueProperty().addListener((obs, old, newValue) -> applyFilters(searchField, statusFilter, filteredData));

        log.debug("Thermo recorder filters setup completed");
    }

    public static void applyFilters(
            TextField searchField,
            ComboBox<String> statusFilter,
            FilteredList<ThermoRecorder> filteredData) {

        String query = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String status = statusFilter.getValue();

        filteredData.setPredicate(r -> {
            boolean matchesSearch = query.isEmpty() ||
                                    r.getSerialNumber().toLowerCase().contains(query) ||
                                    (r.getModel() != null && r.getModel().getName().toLowerCase().contains(query));

            boolean matchesStatus = status == null || "Wszystkie".equals(status) ||
                                    ("Aktywne".equals(status) && r.getStatus() == RecorderStatus.ACTIVE) ||
                                    ("Nieaktywne".equals(status) && r.getStatus() == RecorderStatus.INACTIVE) ||
                                    ("Wzorcowanie".equals(status) && r.getStatus() == RecorderStatus.UNDER_CALIBRATION) ||
                                    ("Wyłączone".equals(status) && r.getStatus() == RecorderStatus.DECOMMISSIONED);

            return matchesSearch && matchesStatus;
        });
    }
}
