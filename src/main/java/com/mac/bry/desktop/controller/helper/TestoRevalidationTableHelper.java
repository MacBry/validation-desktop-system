package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.controller.TestoRevalidationController.SummaryRow;
import com.mac.bry.desktop.controller.TestoRevalidationController.MetrologicalRow;
import com.mac.bry.desktop.controller.TestoRevalidationController.StatsRow;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import atlantafx.base.theme.Styles;
import java.util.function.Consumer;

public class TestoRevalidationTableHelper {

    public static void setupSummaryTable(
            TableView<SummaryRow> table,
            TableColumn<SummaryRow, String> colPosName,
            TableColumn<SummaryRow, String> colPosSn,
            TableColumn<SummaryRow, String> colPosModel,
            TableColumn<SummaryRow, String> colPosCert,
            TableColumn<SummaryRow, String> colPosCertValid,
            TableColumn<SummaryRow, Integer> colPosCount,
            TableColumn<SummaryRow, String> colPosStatus) {

        colPosName.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getPositionName()));
        colPosSn.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getSerialNumber()));
        colPosModel.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getModel()));
        colPosCert.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getCertificateNumber()));
        colPosCertValid.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getValidityDate()));
        colPosCount.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getPointCount()));
        colPosStatus.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getStatus()));
    }

    public static void setupMetrologicalTable(
            TableView<MetrologicalRow> table,
            TableColumn<MetrologicalRow, String> colMetroPos,
            TableColumn<MetrologicalRow, String> colMetroSn,
            TableColumn<MetrologicalRow, String> colMetroMin,
            TableColumn<MetrologicalRow, String> colMetroMax,
            TableColumn<MetrologicalRow, String> colMetroAvg,
            TableColumn<MetrologicalRow, String> colMetroMkt,
            TableColumn<MetrologicalRow, String> colMetroUnc,
            TableColumn<MetrologicalRow, String> colMetroSpikes,
            TableColumn<MetrologicalRow, String> colMetroDrift) {

        colMetroPos.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getPositionName()));
        colMetroSn.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getSerialNumber()));
        colMetroMin.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getMinTemp()));
        colMetroMax.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getMaxTemp()));
        colMetroAvg.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getAvgTemp()));
        colMetroMkt.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getMktTemp()));
        colMetroUnc.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getUncertainty()));
        colMetroSpikes.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getSpikes()));
        colMetroDrift.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getDriftClassification()));

        colMetroDrift.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label tag = new Label(item);
                tag.getStyleClass().add("tag");
                tag.getStyleClass().add(switch (item) {
                    case "STABLE" -> Styles.SUCCESS;
                    case "SPIKE"  -> Styles.ACCENT;
                    case "DRIFT"  -> Styles.DANGER;
                    default       -> Styles.WARNING;
                });
                setGraphic(tag); setText(null);
            }
        });
    }

    public static void setupStatsTable(
            TableView<StatsRow> table,
            TableColumn<StatsRow, String> colStatsPos,
            TableColumn<StatsRow, Double> colStatsMedian,
            TableColumn<StatsRow, Double> colStatsStdDev,
            TableColumn<StatsRow, Double> colStatsRsd,
            TableColumn<StatsRow, Double> colStatsSkewness,
            TableColumn<StatsRow, Double> colStatsKurtosis,
            TableColumn<StatsRow, Double> colStatsCp,
            TableColumn<StatsRow, Double> colStatsCpk,
            TableColumn<StatsRow, Double> colStatsJbPVal,
            TableColumn<StatsRow, Void> colStatsAction,
            Consumer<StatsRow> diagnosticsTrigger) {

        colStatsPos.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleStringProperty(cellData.getValue().getPositionName()));
        colStatsMedian.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getMedian()));
        colStatsStdDev.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getStdDev()));
        colStatsRsd.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getRsd()));
        colStatsSkewness.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getSkewness()));
        colStatsKurtosis.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getKurtosis()));
        colStatsCp.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getCp()));
        colStatsCpk.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getCpk()));
        colStatsJbPVal.setCellValueFactory(cellData -> cellData.getValue() == null ? null : new SimpleObjectProperty<>(cellData.getValue().getJbPVal()));

        colStatsMedian.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.2f °C", item));
            }
        });

        colStatsStdDev.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.3f °C", item));
            }
        });

        colStatsRsd.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.2f%%", item));
            }
        });

        colStatsSkewness.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.3f", item));
            }
        });

        colStatsKurtosis.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.3f", item));
            }
        });

        colStatsCp.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(String.format("%.3f", item));
                setStyle("");
                if (item < 1.0) setStyle("-fx-text-fill: -color-danger-emphasis;");
                else if (item < 1.33) setStyle("-fx-text-fill: -color-warning-emphasis;");
                else setStyle("-fx-text-fill: -color-success-emphasis;");
            }
        });

        colStatsCpk.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(String.format("%.3f", item));
                setStyle("");
                if (item < 1.0) setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
                else if (item < 1.33) setStyle("-fx-text-fill: -color-warning-emphasis; -fx-font-weight: bold;");
                else setStyle("-fx-text-fill: -color-success-emphasis;");
            }
        });

        colStatsJbPVal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(String.format("%.4f", item));
                setStyle("");
                if (item < 0.05) {
                    setStyle("-fx-text-fill: -color-warning-emphasis;");
                    setTooltip(new Tooltip("Rozkład odbiega od normalnego (p < 0.05)"));
                } else {
                    setStyle("-fx-text-fill: -color-success-emphasis;");
                }
            }
        });

        colStatsAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Diagnozuj 📊");
            {
                btn.getStyleClass().add(Styles.ACCENT);
                btn.setOnAction(e -> {
                    StatsRow row = getTableView().getItems().get(getIndex());
                    diagnosticsTrigger.accept(row);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }
}
