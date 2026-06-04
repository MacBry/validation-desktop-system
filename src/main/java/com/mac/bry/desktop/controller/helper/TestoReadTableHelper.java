package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestoReadTableHelper {

    public static void setupTableColumns(
            TableColumn<ThermoMeasurementPoint, Integer> colIndex,
            TableColumn<ThermoMeasurementPoint, LocalDateTime> colTime,
            TableColumn<ThermoMeasurementPoint, Double> colTemp) {

        colIndex.setCellValueFactory(new PropertyValueFactory<>("measurementIndex"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("timestampLocal"));
        colTemp.setCellValueFactory(new PropertyValueFactory<>("rawCelsius"));

        colTime.setCellFactory(column -> new TableCell<>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(formatter));
                }
            }
        });
    }
}
