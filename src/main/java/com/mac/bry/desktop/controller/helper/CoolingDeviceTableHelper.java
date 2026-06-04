package com.mac.bry.desktop.controller.helper;

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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoolingDeviceTableHelper {

    public static void setupMasterTable(
            TableView<CoolingDevice> deviceTable,
            TableColumn<CoolingDevice, String> inventoryCol,
            TableColumn<CoolingDevice, String> nameCol,
            TableColumn<CoolingDevice, String> deptCol,
            TableColumn<CoolingDevice, String> chambersCountCol,
            TableColumn<CoolingDevice, String> statusCol) {

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

        if (statusCol != null) {
            statusCol.setCellValueFactory(d -> {
                com.mac.bry.desktop.model.DeviceStatus s = d.getValue().getStatus();
                return new SimpleStringProperty(s != null ? s.getDisplayName() : "Aktywne");
            });
        }

        log.debug("Master table setup completed");
    }

    public static void setupDetailTable(
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

    public static void setupFilters(
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

    private static void applyFilters(TextField searchField, ComboBox<String> chamberFilter, FilteredList<CoolingDevice> filteredData) {
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
}
