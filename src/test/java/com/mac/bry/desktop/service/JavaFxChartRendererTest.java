package com.mac.bry.desktop.service;

import com.mac.bry.desktop.controller.JavaFxToolkitExtension;
import com.mac.bry.desktop.controller.helper.TestoRevalidationChartHelper;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wykres wielokanałowy trafiający do sekcji 3 zintegrowanego raportu rewalidacji.
 * <p>
 * Powstał przy zamianie zrzutu z ekranu na rendering off-screen. Zrzut fotografował
 * wykres z Kroku 3, który w FXML ma zadaną tylko wysokość — jego szerokość, a przez
 * to proporcje i gęstość etykiet osi, zależały od rozmiaru okna aplikacji w chwili
 * generowania pakietu. Ten sam raport wygenerowany na innym monitorze wyglądał inaczej.
 */
@ExtendWith(JavaFxToolkitExtension.class)
class JavaFxChartRendererTest {

    private static final int EXPECTED_WIDTH = 1000;
    private static final int EXPECTED_HEIGHT = 480;

    private final JavaFxChartRenderer renderer = new JavaFxChartRenderer();

    private static ThermoMeasurementSeries seriesOf(double base, int count) {
        LocalDateTime start = LocalDateTime.of(2026, 8, 5, 8, 0);
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .timestampLocal(start.plusMinutes(15L * i))
                    .rawCelsius(base + Math.sin(i * 0.4))
                    .build());
        }
        return ThermoMeasurementSeries.builder().measurements(points).build();
    }

    /** Trzy pozycje siatki, kolejność zachowana, żeby test parytetu był deterministyczny. */
    private static RevalidationSession sessionWithThreePositions() {
        Map<GridPosition, PositionData> positions = new LinkedHashMap<>();
        positions.put(GridPosition.TOP_FRONT_LEFT,
                PositionData.builder().serialNumber("SN-1").series(seriesOf(4.0, 24)).build());
        positions.put(GridPosition.TOP_FRONT_RIGHT,
                PositionData.builder().serialNumber("SN-2").series(seriesOf(5.0, 24)).build());
        positions.put(GridPosition.BOTTOM_BACK_LEFT,
                PositionData.builder().serialNumber("SN-3").series(seriesOf(3.0, 24)).build());

        return RevalidationSession.builder().assignedPositions(positions).build();
    }

    /** Uruchamia zadanie na wątku JavaFX i oddaje jego wynik wołającemu. */
    private static <T> T onFxThread(FxCall<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                result.set(call.get());
            } catch (Exception e) {
                failure.set(e);
            } finally {
                latch.countDown();
            }
        });

        assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("rendering na wątku JavaFX nie zdążył się zakończyć")
                .isTrue();
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    @FunctionalInterface
    private interface FxCall<T> {
        T get() throws Exception;
    }

    @Test
    @DisplayName("Wykres sesji ma stałe wymiary niezależne od stanu UI")
    void sessionChartHasFixedDimensions() throws Exception {
        RevalidationSession session = sessionWithThreePositions();

        File png = onFxThread(() -> renderer.renderSessionChartToPng(session));

        try {
            var image = ImageIO.read(png);
            assertThat(image).as("plik PNG musi dać się odczytać").isNotNull();
            assertThat(image.getWidth())
                    .as("szerokość nie może zależeć od rozmiaru okna aplikacji")
                    .isEqualTo(EXPECTED_WIDTH);
            assertThat(image.getHeight()).isEqualTo(EXPECTED_HEIGHT);
        } finally {
            png.delete();
        }
    }

    @Test
    @DisplayName("Dwa przebiegi renderowania tej samej sesji dają identyczny obraz")
    void sessionChartIsReproducible() throws Exception {
        RevalidationSession session = sessionWithThreePositions();

        File first = onFxThread(() -> renderer.renderSessionChartToPng(session));
        File second = onFxThread(() -> renderer.renderSessionChartToPng(session));

        try {
            var imageA = ImageIO.read(first);
            var imageB = ImageIO.read(second);

            assertThat(imageA.getWidth()).isEqualTo(imageB.getWidth());
            assertThat(imageA.getHeight()).isEqualTo(imageB.getHeight());

            for (int x = 0; x < imageA.getWidth(); x += 7) {
                for (int y = 0; y < imageA.getHeight(); y += 7) {
                    assertThat(imageA.getRGB(x, y))
                            .as("piksel (%d, %d) różni się między przebiegami", x, y)
                            .isEqualTo(imageB.getRGB(x, y));
                }
            }
        } finally {
            first.delete();
            second.delete();
        }
    }

    @Test
    @DisplayName("Parytet z wykresem ekranowym: te same serie, te same nazwy, te same punkty")
    void offScreenChartMatchesTheOnScreenOne() throws Exception {
        RevalidationSession session = sessionWithThreePositions();

        List<String> offScreenNames = new ArrayList<>();
        List<Integer> offScreenSizes = new ArrayList<>();
        List<String> onScreenNames = new ArrayList<>();
        List<Integer> onScreenSizes = new ArrayList<>();

        onFxThread(() -> {
            for (XYChart.Series<Number, Number> s : renderer.buildSessionChart(session).getData()) {
                offScreenNames.add(s.getName());
                offScreenSizes.add(s.getData().size());
            }

            NumberAxis xAxis = new NumberAxis();
            LineChart<Number, Number> onScreen = new LineChart<>(xAxis, new NumberAxis());
            TestoRevalidationChartHelper.renderMultiChannelChart(onScreen, xAxis, session);
            for (XYChart.Series<Number, Number> s : onScreen.getData()) {
                onScreenNames.add(s.getName());
                onScreenSizes.add(s.getData().size());
            }
            return null;
        });

        assertThat(offScreenNames)
                .as("dokument i ekran muszą pokazywać te same pozycje siatki")
                .containsExactlyElementsOf(onScreenNames)
                .hasSize(3);
        assertThat(offScreenSizes).containsExactlyElementsOf(onScreenSizes);
    }

    @Test
    @DisplayName("Sesja bez pozycji nie wywraca generowania pakietu")
    void emptySessionStillRenders() throws Exception {
        RevalidationSession empty = RevalidationSession.builder()
                .assignedPositions(new LinkedHashMap<>())
                .build();

        File png = onFxThread(() -> renderer.renderSessionChartToPng(empty));

        try {
            assertThat(png).exists();
            assertThat(ImageIO.read(png)).isNotNull();
        } finally {
            png.delete();
        }
    }
}