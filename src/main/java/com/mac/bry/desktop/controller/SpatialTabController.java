package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
import com.mac.bry.desktop.model.RevalidationSession;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SpatialTabController {

    @FXML private Label lblMaxDeltaGlobal;
    @FXML private Label lblMeanDeltaGlobal;
    @FXML private Label lblMaxVerticalGradient;
    @FXML private Label lblMeanVerticalGradient;
    @FXML private LineChart<Number, Number> deltaTimeChart;
    @FXML private NumberAxis deltaTimeAxis;

    @FXML private TableView<LevelRow> levelSummaryTable;
    @FXML private TableColumn<LevelRow, String> colLevelName;
    @FXML private TableColumn<LevelRow, String> colLevelMeanDelta;
    @FXML private TableColumn<LevelRow, String> colLevelMaxDelta;

    public record LevelRow(String name, double meanDelta, double maxDelta) {}

    @FXML
    public void initialize() {
        setupLevelSummaryTable();
    }

    private void setupLevelSummaryTable() {
        if (colLevelName == null) return;
        colLevelName.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        colLevelMeanDelta.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(String.format("%.3f", cell.getValue().meanDelta())));
        colLevelMaxDelta.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(String.format("%.3f", cell.getValue().maxDelta())));
    }

    public void setSpatialData(SpatialStatsResult result) {
        if (result == null) {
            clearData();
            return;
        }

        if (lblMaxDeltaGlobal != null) lblMaxDeltaGlobal.setText(String.format("%.3f", result.getMaxSpatialRange()));
        if (lblMeanDeltaGlobal != null) lblMeanDeltaGlobal.setText(String.format("%.3f", result.getMeanSpatialRange()));
        if (lblMaxVerticalGradient != null) lblMaxVerticalGradient.setText(String.format("%.3f", result.getMaxVerticalGradient()));
        if (lblMeanVerticalGradient != null) lblMeanVerticalGradient.setText(String.format("%.3f", result.getMeanVerticalGradient()));

        if (deltaTimeChart != null) {
            deltaTimeChart.getData().clear();
            XYChart.Series<Number, Number> globalSeries = com.mac.bry.desktop.controller.helper.TestoRevalidationChartHelper.buildDeltaSeries("ΔT globalny", result.getSpatialRangesOverTime());
            XYChart.Series<Number, Number> topSeries    = com.mac.bry.desktop.controller.helper.TestoRevalidationChartHelper.buildDeltaSeries("ΔT GÓRA",    result.getSpatialRangesOverTimeTop());
            XYChart.Series<Number, Number> bottomSeries = com.mac.bry.desktop.controller.helper.TestoRevalidationChartHelper.buildDeltaSeries("ΔT DÓŁ",     result.getSpatialRangesOverTimeBottom());
            deltaTimeChart.getData().addAll(List.of(globalSeries, topSeries, bottomSeries));
        }

        if (levelSummaryTable != null && result.hasLevelData()) {
            ObservableList<LevelRow> rows = FXCollections.observableArrayList(
                new LevelRow("🔼 GÓRA (TOP)",    result.getMeanRangeTop(),    result.getMaxRangeTop()),
                new LevelRow("🔽 DÓŁ (BOTTOM)",  result.getMeanRangeBottom(), result.getMaxRangeBottom())
            );
            levelSummaryTable.setItems(rows);
        }
    }

    private void clearData() {
        if (lblMaxDeltaGlobal != null) lblMaxDeltaGlobal.setText("—");
        if (lblMeanDeltaGlobal != null) lblMeanDeltaGlobal.setText("—");
        if (lblMaxVerticalGradient != null) lblMaxVerticalGradient.setText("—");
        if (lblMeanVerticalGradient != null) lblMeanVerticalGradient.setText("—");
        if (deltaTimeChart != null) deltaTimeChart.getData().clear();
        if (levelSummaryTable != null) levelSummaryTable.getItems().clear();
    }
}
