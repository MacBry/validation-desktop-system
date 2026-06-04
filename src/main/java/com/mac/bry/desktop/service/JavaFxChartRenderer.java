package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.image.WritableImage;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serwis odpowiedzialny za renderowanie wykresu JavaFX LineChart do pliku PNG.
 * Operuje off-screen (poza ekranem) – musi być wywoływany z wątku JavaFX Application Thread.
 *
 * Wydzielony z TestoRevalidationController w celu zgodności z zasadą SRP.
 */
@Service
@Slf4j
public class JavaFxChartRenderer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double CHART_WIDTH = 760.0;
    private static final double CHART_HEIGHT = 420.0;

    /**
     * Renderuje listę punktów pomiarowych jako wykres liniowy i zapisuje jako plik PNG.
     *
     * @param measurements lista punktów pomiarowych jednej serii
     * @return tymczasowy plik PNG z wyrenderowanym wykresem
     * @throws IOException w przypadku błędu zapisu pliku
     */
    public File renderSeriesToPng(List<ThermoMeasurementPoint> measurements) throws IOException {
        log.debug("Renderowanie off-screen wykresu dla {} punktów pomiarowych", measurements.size());

        NumberAxis xAxis = buildXAxis(measurements);
        NumberAxis yAxis = buildYAxis();
        LineChart<Number, Number> chart = buildChart(xAxis, yAxis, measurements);

        // Dummy scene wymagana przez JavaFX, aby SnapshotParameters działało poza Stage
        new javafx.scene.Scene(chart);

        WritableImage image = chart.snapshot(new SnapshotParameters(), null);
        File tempFile = File.createTempFile("reval_chart_snap_single_", ".png");
        java.awt.image.BufferedImage bufImg = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        ImageIO.write(bufImg, "png", tempFile);

        log.debug("Wykres zapisany do pliku tymczasowego: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    /**
     * Wykonuje zrzut istniejącego wykresu z UI (multichannel) do pliku PNG.
     *
     * @param chart  wyrenderowany na ekranie wielokanałowy wykres
     * @return tymczasowy plik PNG
     */
    public File snapshotExistingChart(LineChart<Number, Number> chart) throws IOException {
        log.debug("Zrzut ekranu istniejącego wykresu wielokanałowego");
        WritableImage image = chart.snapshot(new SnapshotParameters(), null);
        File tempFile = File.createTempFile("reval_chart_snap_", ".png");
        java.awt.image.BufferedImage bufImg = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        ImageIO.write(bufImg, "png", tempFile);
        return tempFile;
    }

    // ---- Helpers ----

    private NumberAxis buildXAxis(List<ThermoMeasurementPoint> measurements) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                int idx = object.intValue();
                if (idx >= 1 && idx <= measurements.size()) {
                    return measurements.get(idx - 1).getTimestampLocal().format(TIME_FMT);
                }
                return "";
            }
            @Override
            public Number fromString(String string) { return 0; }
        });
        return xAxis;
    }

    private NumberAxis buildYAxis() {
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        return yAxis;
    }

    private LineChart<Number, Number> buildChart(NumberAxis xAxis, NumberAxis yAxis,
                                                  List<ThermoMeasurementPoint> measurements) {
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setAnimated(false);
        chart.setPrefSize(CHART_WIDTH, CHART_HEIGHT);
        chart.setMinSize(CHART_WIDTH, CHART_HEIGHT);
        chart.setMaxSize(CHART_WIDTH, CHART_HEIGHT);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (int i = 0; i < measurements.size(); i++) {
            series.getData().add(new XYChart.Data<>(i + 1, measurements.get(i).getRawCelsius()));
        }
        chart.getData().add(series);
        return chart;
    }

    /**
     * Renderuje wiele serii pomiarowych do jednego wykresu off-screen i zapisuje jako plik PNG.
     */
    public File renderMultipleSeriesToPng(List<ThermoMeasurementSeries> seriesList) throws IOException {
        log.debug("Renderowanie off-screen wykresu dla {} serii pomiarowych", seriesList.size());

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Punkt pomiarowy");
        yAxis.setLabel("Temperatura (°C)");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefSize(800, 400);

        for (ThermoMeasurementSeries series : seriesList) {
            var chartSeries = new XYChart.Series<Number, Number>();
            chartSeries.setName(series.getThermoRecorder().getSerialNumber());
            List<ThermoMeasurementPoint> pts = series.getMeasurements();
            if (pts != null) {
                for (int i = 0; i < pts.size(); i++) {
                    chartSeries.getData().add(new XYChart.Data<>(i + 1, pts.get(i).getRawCelsius()));
                }
            }
            chart.getData().add(chartSeries);
        }

        new javafx.scene.Scene(chart);
        WritableImage image = chart.snapshot(new SnapshotParameters(), null);

        File tempFile = File.createTempFile("reval_chart_snap_hist_", ".png");
        java.awt.image.BufferedImage bufImg = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        ImageIO.write(bufImg, "png", tempFile);
        return tempFile;
    }
}
