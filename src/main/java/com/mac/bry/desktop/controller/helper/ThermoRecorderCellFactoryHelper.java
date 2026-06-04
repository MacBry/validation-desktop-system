package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import java.util.function.Consumer;
import java.util.function.Function;

public class ThermoRecorderCellFactoryHelper {

    public static void setupStatusColumn(TableColumn<ThermoRecorder, String> statusColumn) {
        statusColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label();
                    tagLabel.getStyleClass().add("tag");

                    try {
                        RecorderStatus status = RecorderStatus.valueOf(item);
                        switch (status) {
                            case ACTIVE -> {
                                tagLabel.setText("Aktywny");
                                tagLabel.getStyleClass().add(Styles.SUCCESS);
                            }
                            case UNDER_CALIBRATION -> {
                                tagLabel.setText("Wzorcowany");
                                tagLabel.getStyleClass().add(Styles.ACCENT);
                            }
                            case INACTIVE -> {
                                tagLabel.setText("Nieaktywny");
                            }
                            case DECOMMISSIONED -> {
                                tagLabel.setText("Wyłączone z użytku");
                                tagLabel.getStyleClass().add(Styles.DANGER);
                            }
                        }
                    } catch (Exception e) {
                        tagLabel.setText(item);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }

    public static void setupCalibrationColumn(
            TableColumn<ThermoRecorder, String> calibrationColumn,
            Function<ThermoRecorder, String> calibrationStatusProvider) {

        calibrationColumn.setCellValueFactory(d -> new SimpleStringProperty(calibrationStatusProvider.apply(d.getValue())));
        calibrationColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label(item);
                    tagLabel.getStyleClass().add("tag");

                    if (item.contains("WAŻNE")) {
                        tagLabel.getStyleClass().add(Styles.SUCCESS);
                    } else if (item.contains("WYGASA")) {
                        tagLabel.getStyleClass().add(Styles.WARNING);
                    } else { // NIEWAŻNE / BRAK WZORCOWANIA
                        tagLabel.getStyleClass().add(Styles.DANGER);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }

    public static void setupActionsColumn(
            TableColumn<ThermoRecorder, Void> actionsColumn,
            Consumer<ThermoRecorder> onEdit,
            Consumer<ThermoRecorder> onHistory,
            Consumer<ThermoRecorder> onAudit) {

        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edytuj");
            private final Button historyBtn = new Button("Wzorcowania");
            private final Button auditBtn = new Button("Audit");
            private final HBox container = new HBox(6, editBtn, historyBtn, auditBtn);

            {
                editBtn.getStyleClass().addAll("button-sm", "success");
                historyBtn.getStyleClass().addAll("button-sm", "accent");
                auditBtn.getStyleClass().addAll("button-sm", "danger");

                editBtn.setOnAction(e -> onEdit.accept(getTableView().getItems().get(getIndex())));
                historyBtn.setOnAction(e -> onHistory.accept(getTableView().getItems().get(getIndex())));
                auditBtn.setOnAction(e -> onAudit.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });
    }
}
