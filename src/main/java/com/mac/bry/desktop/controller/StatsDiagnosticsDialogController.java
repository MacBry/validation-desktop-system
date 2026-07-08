package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StatsDiagnosticsDialogController {

    @FXML private Label lblSensorTitle;
    @FXML private Label lblSpcStats;

    // Wykresy SPC
    @FXML private LineChart<Number, Number> xBarChart;
    @FXML private LineChart<Number, Number> sChart;

    // Nelson Rules
    @FXML private ListView<String> lstNelsonViolations;

    // Tabela Defrost
    @FXML private TableView<DefrostCycle> defrostTable;
    @FXML private TableColumn<DefrostCycle, String> colDefrostStart;
    @FXML private TableColumn<DefrostCycle, String> colDefrostEnd;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostDuration;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostMax;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostAmp;

    // ---- Zakładka 3: Jednorodność Przestrzenna ----
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FMT_DELTA = "%.3f";

    @FXML
    public void initialize() {
        setupDefrostTable();
    }

    // -----------------------------------------------------------------------
    // Konfiguracja tabel
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Dane dla zakładki SPC + Defrost (bez zmian)
    // -----------------------------------------------------------------------

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

        // 3. Weryfikacja reguł stabilności Nelsona
        log.info("Rozpoczęcie detekcji reguł Nelsona dla: {}", positionLabel);
        List<NelsonRulesDetector.Violation> xbarViolations = NelsonRulesDetector.detectXBarViolations(spcData);
        List<NelsonRulesDetector.Violation> sViolations = NelsonRulesDetector.detectSViolations(spcData);
        log.info("Wykryto naruszeń X-bar: {}, S: {}", xbarViolations.size(), sViolations.size());

        ObservableList<String> nelsonItems = FXCollections.observableArrayList();
        for (NelsonRulesDetector.Violation v : xbarViolations) {
            String interpretation = com.mac.bry.desktop.service.stats.NelsonRulesInterpreter.getXBarInterpretation(v.getRuleNumber());
            String msg = String.format("[Karta I] Punkt %d: %s\n> Interpretacja: %s\n", v.getSubgroupIndex(), v.getDescription(), interpretation);
            log.info("Dodawanie naruszenia I: {}", msg);
            nelsonItems.add(msg);
        }
        for (NelsonRulesDetector.Violation v : sViolations) {
            String interpretation = com.mac.bry.desktop.service.stats.NelsonRulesInterpreter.getSChartInterpretation(v.getRuleNumber());
            String msg = String.format("[Karta MR] Para %d-%d: %s\n> Interpretacja: %s\n", v.getSubgroupIndex() - 1, v.getSubgroupIndex(), v.getDescription(), interpretation);
            log.info("Dodawanie naruszenia MR: {}", msg);
            nelsonItems.add(msg);
        }

        if (lstNelsonViolations != null) {
            log.info("Ustawianie elementów w lstNelsonViolations (rozmiar: {})", nelsonItems.size());
            lstNelsonViolations.setCellFactory(lv -> {
                ListCell<String> cell = new ListCell<String>() {
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
                };
                return cell;
            });
            lstNelsonViolations.setItems(nelsonItems);
        } else {
            log.error("UWAGA: lstNelsonViolations jest NULL!");
        }

        // 4. Detekcja cykli defrostu
        List<DefrostCycle> defrostCycles = DefrostCycleDetector.detectCycles(measurements, positionLabel, 0.25, 1.5);
        defrostTable.setItems(FXCollections.observableArrayList(defrostCycles));
    }

    // -----------------------------------------------------------------------
    // Renderowanie kart SPC (niezmienione)
    // -----------------------------------------------------------------------

    private void renderXBarChart(ControlChartData data) {
        xBarChart.getData().clear();

        XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
        meanSeries.setName("Wartości indywidualne");
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL (Górna Granica)");
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL (Dolna Granica)");
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL (Średnia)");

        List<Double> values = data.getIndividualValues();
        for (int i = 0; i < values.size(); i++) {
            int x = i + 1;
            meanSeries.getData().add(new XYChart.Data<>(x, values.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getIUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getILcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getICentralLine()));
        }

        xBarChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, meanSeries));
    }

    private void renderSChart(ControlChartData data) {
        sChart.getData().clear();

        XYChart.Series<Number, Number> sSeries = new XYChart.Series<>();
        sSeries.setName("Ruchomy rozstęp (MR)");
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL");
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL");
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL");

        List<Double> mrValues = data.getMovingRanges();
        for (int i = 0; i < mrValues.size(); i++) {
            int x = i + 2; // MR_1 jest rysowany od x=2 (dla pary 1-2)
            sSeries.getData().add(new XYChart.Data<>(x, mrValues.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getMrUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getMrLcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getMrCentralLine()));
        }

        sChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, sSeries));
    }

    @FXML
    public void handleClose() {
        ((Stage) lblSensorTitle.getScene().getWindow()).close();
    }
}
