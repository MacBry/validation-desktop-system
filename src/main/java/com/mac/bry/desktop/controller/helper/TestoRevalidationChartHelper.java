package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class TestoRevalidationChartHelper {

    public static void renderMultiChannelChart(LineChart<Number, Number> chart, NumberAxis xAxisTime, RevalidationSession session) {
        chart.getData().clear();
        xAxisTime.setForceZeroInRange(false);
        xAxisTime.setMinorTickVisible(false);
        xAxisTime.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number n) {
                int idx = n.intValue();
                var vals = session.getAssignedPositions().values().iterator();
                if (vals.hasNext()) {
                    var s = vals.next().getSeries();
                    if (idx >= 1 && idx <= s.getMeasurements().size())
                        return s.getMeasurements().get(idx - 1).getTimestampLocal()
                                .format(DateTimeFormatter.ofPattern("HH:mm"));
                }
                return "";
            }
            @Override public Number fromString(String s) { return 0; }
        });

        DateTimeFormatter tooltipTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        session.getAssignedPositions().forEach((pos, data) -> {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName(pos.getLabel());
            List<ThermoMeasurementPoint> pts = data.getSeries().getMeasurements();
            for (int i = 0; i < pts.size(); i++) {
                ThermoMeasurementPoint pt = pts.get(i);
                XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(i + 1, pt.getRawCelsius());
                
                dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        String timeStr = pt.getTimestampLocal().format(tooltipTimeFormatter);
                        Tooltip tooltip = new Tooltip(String.format(
                                "Pozycja: %s\nCzas: %s\nTemperatura: %.1f °C", 
                                pos.getLabel(), timeStr, pt.getRawCelsius()
                        ));
                        tooltip.setShowDelay(javafx.util.Duration.millis(50));
                        tooltip.setHideDelay(javafx.util.Duration.millis(50));
                        tooltip.setShowDuration(javafx.util.Duration.INDEFINITE);
                        tooltip.setStyle(
                            "-fx-font-size: 12px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-color: rgba(30, 41, 59, 0.9); " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 8px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 5, 0.0, 0, 2);"
                        );
                        Tooltip.install(newNode, tooltip);

                        newNode.setOnMouseEntered(e -> {
                            newNode.setScaleX(1.8);
                            newNode.setScaleY(1.8);
                            newNode.setCursor(javafx.scene.Cursor.HAND);
                            newNode.setStyle("-fx-background-color: -color-accent-emphasis;");
                        });
                        newNode.setOnMouseExited(e -> {
                            newNode.setScaleX(1.0);
                            newNode.setScaleY(1.0);
                            newNode.setCursor(javafx.scene.Cursor.DEFAULT);
                            newNode.setStyle("");
                        });
                    }
                });
                s.getData().add(dataPoint);
            }
            chart.getData().add(s);
        });
    }

    public static XYChart.Series<Number, Number> buildDeltaSeries(String name, Map<LocalDateTime, Double> data) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        if (data == null || data.isEmpty()) return series;
        int idx = 1;
        for (Map.Entry<LocalDateTime, Double> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(idx++, entry.getValue()));
        }
        return series;
    }
}
