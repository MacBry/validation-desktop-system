package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.security.model.AccessLog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class AccessLogsTableHelper {

    public static void setupAccessLogsTable(
            TableView<AccessLog> table,
            TableColumn<AccessLog, String> timestampCol,
            TableColumn<AccessLog, String> usernameCol,
            TableColumn<AccessLog, String> actionCol,
            TableColumn<AccessLog, String> detailsCol) {

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        timestampCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getTimestamp().format(dtf)));
        usernameCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUsername()));
        actionCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getAction()));
        detailsCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDetails()));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AccessLog item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    setStyle(getStyleForAction(item.getAction()));
                }
            }
        });
    }

    public static void populateLogs(TableView<AccessLog> table, List<AccessLog> logs) {
        table.setItems(FXCollections.observableArrayList(logs));
    }

    private static String getStyleForAction(String action) {
        if ("USB_PROGRAMMING".equals(action)) {
            return "-fx-background-color: #f3e8ff;";
        } else if ("USB_READING".equals(action)) {
            return "-fx-background-color: #ecfdf5;";
        } else if (action != null && action.contains("FAILED")) {
            return "-fx-background-color: #fee2e2;";
        }
        return "";
    }
}
