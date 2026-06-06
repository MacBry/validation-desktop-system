package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.service.stats.ControlChartCalculator;
import com.mac.bry.desktop.service.stats.DefrostCycleDetector;
import com.mac.bry.desktop.service.stats.SpcEngine;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StatsDiagnosticsDialogController {

    @FXML private Label lblSensorTitle;
    @FXML private Label lblSpcStats;

    // Wykresy SPC
    @FXML private LineChart<Number, Number> xBarChart;
    @FXML private LineChart<Number, Number> sChart;

    // Tabela Defrost
    @FXML private TableView<DefrostCycle> defrostTable;
    @FXML private TableColumn<DefrostCycle, String> colDefrostStart;
    @FXML private TableColumn<DefrostCycle, String> colDefrostEnd;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostDuration;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostMax;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostAmp;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupDefrostTable();
    }

    private void setupDefrostTable() {
        colDefrostStart.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getStartTime().format(DATE_FORMATTER)));
        colDefrostEnd.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getEndTime().format(DATE_FORMATTER)));
        colDefrostDuration.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                Math.round(cellData.getValue().getDurationMinutes() * 10.0) / 10.0));
        colDefrostMax.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                Math.round(cellData.getValue().getMaxTemperature() * 100.0) / 100.0));
        colDefrostAmp.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                Math.round(cellData.getValue().getAmplitude() * 100.0) / 100.0));
    }

    public void setSensorData(ThermoMeasurementSeries series, String positionLabel, Double minLimit, Double maxLimit) {
        if (series == null) return;

        lblSensorTitle.setText("Zaawansowana Diagnostyka SPC & Defrost dla pozycji: " + positionLabel);

        List<ThermoMeasurementPoint> measurements = series.getMeasurements();
        double[] rawData = measurements.stream()
                .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                .toArray();

        // 1. Obliczenie wskaźników zdolności (SPC)
        double lsl = minLimit != null ? minLimit : 2.0;
        double usl = maxLimit != null ? maxLimit : 8.0;
        CapabilityIndexes cpIndex = SpcEngine.calculateCapability(rawData, lsl, usl);

        lblSpcStats.setText(String.format(
                "Wskaźnik Cp: %.3f | Wskaźnik Cpk: %.3f (Limity specyfikacji: %.1f - %.1f °C)",
                cpIndex.getCp(), cpIndex.getCpk(), lsl, usl
        ));

        // 2. Obliczenie i rysowanie kart kontrolnych Shewharta
        ControlChartData spcData = ControlChartCalculator.calculateShewhartLimits(rawData);
        renderXBarChart(spcData);
        renderSChart(spcData);

        // 3. Detekcja cykli defrostu
        // rateThreshold = 0.25°C/min, amplitudeThreshold = 1.5°C
        List<DefrostCycle> defrostCycles = DefrostCycleDetector.detectCycles(measurements, positionLabel, 0.25, 1.5);
        defrostTable.setItems(FXCollections.observableArrayList(defrostCycles));
    }

    private void renderXBarChart(ControlChartData data) {
        xBarChart.getData().clear();

        XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
        meanSeries.setName("Średnie podgrup");
        
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL (Górna Granica)");
        
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL (Dolna Granica)");
        
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL (Średnia globalna)");

        List<Double> means = data.getSubgroupMeans();
        for (int i = 0; i < means.size(); i++) {
            int x = i + 1;
            meanSeries.getData().add(new XYChart.Data<>(x, means.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getXBarUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getXBarLcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getXBarCentralLine()));
        }

        xBarChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, meanSeries));
    }

    private void renderSChart(ControlChartData data) {
        sChart.getData().clear();

        XYChart.Series<Number, Number> sSeries = new XYChart.Series<>();
        sSeries.setName("Odchylenia podgrup");

        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL");

        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL");

        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL");

        List<Double> stdDevs = data.getSubgroupStdDevs();
        for (int i = 0; i < stdDevs.size(); i++) {
            int x = i + 1;
            sSeries.getData().add(new XYChart.Data<>(x, stdDevs.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getSCentralLine() * 2.089)); // B4 * sCL
            lclSeries.getData().add(new XYChart.Data<>(x, 0.0)); // B3 = 0.0
            clSeries.getData().add(new XYChart.Data<>(x, data.getSCentralLine()));
        }

        sChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, sSeries));
    }

    @FXML
    public void handleClose() {
        ((Stage) lblSensorTitle.getScene().getWindow()).close();
    }
}
