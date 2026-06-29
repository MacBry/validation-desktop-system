package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import javafx.scene.Scene;
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

    // Proporcja 25:12 odpowiada ramce 500×240 pt, do której raport zbiorczy
    // skaluje ten wykres (TraceabilitySectionRenderer) — obraz wchodzi bez
    // marginesów, a dwukrotna nadpróbkowa zostaje na wydruk.
    private static final double SESSION_CHART_WIDTH = 1000.0;
    private static final double SESSION_CHART_HEIGHT = 480.0;

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
     * Renderuje wielokanałowy wykres sesji off-screen i zapisuje jako PNG —
     * ten, który trafia do sekcji 3 zintegrowanego raportu rewalidacji.
     *
     * <p><b>Dlaczego off-screen, a nie zrzut z ekranu.</b> Do 2026-08-07 raport
     * zbiorczy fotografował wykres widoczny w Kroku 3 ({@code snapshotExistingChart}).
     * Wykres w FXML ma zadaną tylko wysokość, więc jego szerokość — a przez to
     * proporcje, gęstość etykiet osi i łamanie legendy — zależała od rozmiaru okna
     * aplikacji w chwili generowania pakietu. Ten sam raport wygenerowany ponownie
     * na innym monitorze wyglądał inaczej, co w dokumencie walidacyjnym jest
     * problemem z odtwarzalnością. Tutaj wymiary są stałe.
     *
     * <p>Zawartość jest celowo taka sama jak na ekranie: po jednej serii na pozycję
     * siatki, nazwaną etykietą pozycji, oś X indeksowana od 1 z etykietami czasu
     * pierwszej serii. Ekranowy odpowiednik to
     * {@code TestoRevalidationChartHelper.renderMultiChannelChart} — zgodności obu
     * pilnuje test parytetu. Różnią się wyłącznie tym, czego dokument nie potrzebuje:
     * tooltipami i reakcją na kursor.
     *
     * <p>Musi być wywołane z wątku JavaFX Application Thread.
     */
    public File renderSessionChartToPng(RevalidationSession session) throws IOException {
        log.debug("Renderowanie off-screen wykresu sesji dla {} pozycji",
                session.getAssignedPositions().size());

        LineChart<Number, Number> chart = buildSessionChart(session);

        Scene scene = new Scene(chart);
        applyApplicationStylesheet(scene);
        chart.applyCss();
        chart.layout();

        WritableImage image = chart.snapshot(new SnapshotParameters(), null);
        File tempFile = File.createTempFile("reval_chart_session_", ".png");
        java.awt.image.BufferedImage bufImg = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        ImageIO.write(bufImg, "png", tempFile);

        log.debug("Wykres sesji zapisany do pliku tymczasowego: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    /** Widoczne dla testu parytetu z wykresem ekranowym. */
    LineChart<Number, Number> buildSessionChart(RevalidationSession session) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelFormatter(sessionTimeFormatter(session));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("premium-line-chart");
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        chart.setAnimated(false);
        chart.setPrefSize(SESSION_CHART_WIDTH, SESSION_CHART_HEIGHT);
        chart.setMinSize(SESSION_CHART_WIDTH, SESSION_CHART_HEIGHT);
        chart.setMaxSize(SESSION_CHART_WIDTH, SESSION_CHART_HEIGHT);

        session.getAssignedPositions().forEach((pos, data) -> {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(pos.getLabel());
            List<ThermoMeasurementPoint> pts =
                    data.getSeries() != null ? data.getSeries().getMeasurements() : null;
            if (pts != null) {
                for (int i = 0; i < pts.size(); i++) {
                    series.getData().add(new XYChart.Data<>(i + 1, pts.get(i).getRawCelsius()));
                }
            }
            chart.getData().add(series);
        });

        return chart;
    }

    /**
     * Etykiety osi czasu brane z pierwszej serii sesji — wszystkie rejestratory
     * pracują w tym samym oknie i tym samym interwale, więc indeks próbki jest
     * wspólny. Ta sama zasada co na ekranie.
     */
    private StringConverter<Number> sessionTimeFormatter(RevalidationSession session) {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                var positions = session.getAssignedPositions().values().iterator();
                if (!positions.hasNext()) {
                    return "";
                }
                List<ThermoMeasurementPoint> pts = positions.next().getSeries().getMeasurements();
                int idx = value.intValue();
                return (idx >= 1 && idx <= pts.size())
                        ? pts.get(idx - 1).getTimestampLocal().format(TIME_FMT)
                        : "";
            }

            @Override
            public Number fromString(String string) { return 0; }
        };
    }

    private void applyApplicationStylesheet(Scene scene) {
        var css = getClass().getResource("/ui/style.css");
        if (css == null) {
            log.warn("Brak /ui/style.css — wykres sesji powstanie w domyślnym stylu JavaFX");
            return;
        }
        scene.getStylesheets().add(css.toExternalForm());
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
