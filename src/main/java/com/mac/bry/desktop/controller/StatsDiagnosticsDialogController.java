package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.service.stats.ControlChartCalculator;
import com.mac.bry.desktop.service.stats.DefrostCycleDetector;
import com.mac.bry.desktop.service.stats.NelsonRulesDetector;
import com.mac.bry.desktop.service.stats.SpcEngine;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StatsDiagnosticsDialogController {

    @FXML private Label lblSensorTitle;
    @FXML private Label lblSpcStats;

    // Wykresy Shewharta (X-bar & S)
    @FXML private LineChart<Number, Number> xBarChart;
    @FXML private LineChart<Number, Number> sChart;
    @FXML private ListView<String> lstNelsonViolations;

    // Wykresy I-MR
    @FXML private LineChart<Number, Number> iChart;
    @FXML private LineChart<Number, Number> mrChart;
    @FXML private ListView<String> lstNelsonViolationsImr;

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

        // 2. Obliczenie granic i limitów dla obu modeli (Shewhart oraz I-MR)
        ControlChartData spcData = ControlChartCalculator.calculateShewhartLimits(rawData);
        
        // Rysowanie wykresów Shewharta
        renderXBarChart(spcData);
        renderSChart(spcData);

        // Rysowanie wykresów I-MR
        renderIChart(spcData);
        renderMRChart(spcData);

        // 3. Weryfikacja reguł stabilności Nelsona
        // A. Karty Shewharta
        List<NelsonRulesDetector.Violation> xbarViolations = NelsonRulesDetector.detectXBarViolations(spcData);
        List<NelsonRulesDetector.Violation> sViolations = NelsonRulesDetector.detectSViolations(spcData);
        setupNelsonListView(lstNelsonViolations, xbarViolations, sViolations, "Podgrupa", "Podgrupa");

        // B. Karty I-MR
        List<NelsonRulesDetector.Violation> iViolations = NelsonRulesDetector.detectIndividualViolations(spcData);
        List<NelsonRulesDetector.Violation> mrViolations = NelsonRulesDetector.detectMovingRangeViolations(spcData);
        setupNelsonListView(lstNelsonViolationsImr, iViolations, mrViolations, "Punkt", "Para");

        // 4. Detekcja cykli defrostu
        List<DefrostCycle> defrostCycles = DefrostCycleDetector.detectCycles(measurements, positionLabel, 0.25, 1.5);
        defrostTable.setItems(FXCollections.observableArrayList(defrostCycles));
    }

    private void setupNelsonListView(ListView<String> listView,
                                     List<NelsonRulesDetector.Violation> card1Violations,
                                     List<NelsonRulesDetector.Violation> card2Violations,
                                     String card1UnitLabel,
                                     String card2UnitLabel) {
        if (listView == null) return;

        ObservableList<String> nelsonItems = FXCollections.observableArrayList();
        for (NelsonRulesDetector.Violation v : card1Violations) {
            String interpretation = com.mac.bry.desktop.service.stats.NelsonRulesInterpreter.getXBarInterpretation(v.getRuleNumber());
            String msg = String.format("[Karta I/X-Bar] %s %d: %s\n> Interpretacja: %s\n", card1UnitLabel, v.getSubgroupIndex(), v.getDescription(), interpretation);
            nelsonItems.add(msg);
        }
        for (NelsonRulesDetector.Violation v : card2Violations) {
            String interpretation = com.mac.bry.desktop.service.stats.NelsonRulesInterpreter.getSChartInterpretation(v.getRuleNumber());
            String msg = String.format("[Karta MR/S] %s %d: %s\n> Interpretacja: %s\n", card2UnitLabel, v.getSubgroupIndex(), v.getDescription(), interpretation);
            nelsonItems.add(msg);
        }

        listView.setCellFactory(lv -> new ListCell<String>() {
            private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();
            {
                textNode.wrappingWidthProperty().bind(lv.widthProperty().subtract(40));
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle(null);
                } else {
                    textNode.setText(item);
                    textNode.setStyle("-fx-fill: #b91c1c; -fx-font-weight: bold;");
                    setGraphic(textNode);
                    setText(null);
                    setStyle("-fx-padding: 10px; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1 0;");
                }
            }
        });
        listView.setItems(nelsonItems);
    }

    // --- Renderowanie kart Shewharta ---
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
            uclSeries.getData().add(new XYChart.Data<>(x, data.getSUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getSLcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getSCentralLine()));
        }

        sChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, sSeries));
    }

    // --- Renderowanie kart I-MR ---
    private void renderIChart(ControlChartData data) {
        iChart.getData().clear();

        XYChart.Series<Number, Number> valSeries = new XYChart.Series<>();
        valSeries.setName("Wartości indywidualne");
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL");
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL");
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL");

        List<Double> values = data.getIndividualValues();
        for (int i = 0; i < values.size(); i++) {
            int x = i + 1;
            valSeries.getData().add(new XYChart.Data<>(x, values.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getIUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getILcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getICentralLine()));
        }

        iChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, valSeries));
    }

    private void renderMRChart(ControlChartData data) {
        mrChart.getData().clear();

        XYChart.Series<Number, Number> mrSeries = new XYChart.Series<>();
        mrSeries.setName("Ruchomy rozstęp (MR)");
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL");
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL");
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL");

        List<Double> mrValues = data.getMovingRanges();
        for (int i = 0; i < mrValues.size(); i++) {
            int x = i + 2; // Rozstęp MR_1 dotyczy pary 1-2 (x=2)
            mrSeries.getData().add(new XYChart.Data<>(x, mrValues.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getMrUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getMrLcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getMrCentralLine()));
        }

        mrChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, mrSeries));
    }

    @FXML
    public void handleClose() {
        ((Stage) lblSensorTitle.getScene().getWindow()).close();
    }
}
