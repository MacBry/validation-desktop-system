package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.model.ProcedureRow;
import com.mac.bry.desktop.model.DetailRow;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.function.Consumer;

public class ProceduresTableHelper {

    public static void setupTableColumns(
            TableColumn<ProcedureRow, String> typeCol,
            TableColumn<ProcedureRow, String> locationCol,
            TableColumn<ProcedureRow, String> dateCol,
            TableColumn<ProcedureRow, String> sensorsCol,
            TableColumn<ProcedureRow, Integer> countCol,
            TableColumn<ProcedureRow, String> statusCol,
            TableColumn<ProcedureRow, Void> actionsCol,
            Consumer<ProcedureRow> pdfActionHandler) {

        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType()));
        locationCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLocation()));
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDateImported()));
        sensorsCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSensors()));
        countCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getMeasurementsCount()).asObject());

        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGxpStatus()));
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label(item);
                    tagLabel.getStyleClass().add("tag");
                    if ("ZATWIERDZONA GxP".equals(item)) {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.SUCCESS);
                    } else {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.WARNING);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });

        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button pdfBtn = new Button("💾 PDF");
            {
                pdfBtn.getStyleClass().addAll("button-sm", "accent");
                pdfBtn.setStyle("-fx-font-weight: bold;");
                pdfBtn.setOnAction(e -> pdfActionHandler.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pdfBtn);
            }
        });
    }

    public static void setupDetailsTableColumns(
            TableColumn<DetailRow, String> colDetailPos,
            TableColumn<DetailRow, String> colDetailSn,
            TableColumn<DetailRow, String> colDetailMin,
            TableColumn<DetailRow, String> colDetailMax,
            TableColumn<DetailRow, String> colDetailAvg,
            TableColumn<DetailRow, String> colDetailMkt,
            TableColumn<DetailRow, String> colDetailUncertainty,
            TableColumn<DetailRow, String> colDetailSpikes,
            TableColumn<DetailRow, String> colDetailDrift) {

        colDetailPos.setCellValueFactory(new PropertyValueFactory<>("positionName"));
        colDetailSn.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colDetailMin.setCellValueFactory(new PropertyValueFactory<>("minTemp"));
        colDetailMax.setCellValueFactory(new PropertyValueFactory<>("maxTemp"));
        colDetailAvg.setCellValueFactory(new PropertyValueFactory<>("avgTemp"));
        colDetailMkt.setCellValueFactory(new PropertyValueFactory<>("mktTemp"));
        colDetailUncertainty.setCellValueFactory(new PropertyValueFactory<>("uncertainty"));
        colDetailSpikes.setCellValueFactory(new PropertyValueFactory<>("spikes"));
        colDetailDrift.setCellValueFactory(new PropertyValueFactory<>("driftClassification"));

        colDetailDrift.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label(item);
                    tagLabel.getStyleClass().add("tag");
                    if ("STABLE".equals(item)) {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.SUCCESS);
                    } else if ("SPIKE".equals(item)) {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.ACCENT);
                    } else if ("DRIFT".equals(item)) {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.DANGER);
                    } else {
                        tagLabel.getStyleClass().add(atlantafx.base.theme.Styles.WARNING);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }
}
