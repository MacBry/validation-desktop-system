package com.mac.bry.desktop.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Charakterystyka serii w sekcji „1. Metryka Urządzenia oraz Parametry Odczytu".
 *
 * <p>Testy statystyk działają wszędzie. Test end-to-end jest ograniczony do Windows,
 * bo {@link TestoPdfReportService} ładuje czcionkę ze ścieżki {@code C:\Windows\Fonts\arial.ttf}
 * i na CI (Linux) generowanie raportu w ogóle się nie powiedzie. To ograniczenie
 * istniejące, nie wprowadzone tutaj — dlatego pozostałe testy raportów mockują ten serwis.
 */
class TestoPdfReportServiceTest {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 5, 13, 30, 0);

    private static ThermoMeasurementPoint point(int index, LocalDateTime at, double celsius) {
        return ThermoMeasurementPoint.builder()
                .measurementIndex(index)
                .timestampLocal(at)
                .rawCelsius(celsius)
                .build();
    }

    /** Seria 15-minutowa o znanych ekstremach: min 2,4 °C, max 8,1 °C, średnia 5,10 °C. */
    private static List<ThermoMeasurementPoint> sampleSeries() {
        double[] temps = {4.5, 2.4, 8.1, 5.5};
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < temps.length; i++) {
            points.add(point(i + 1, START.plusMinutes(15L * i), temps[i]));
        }
        return points;
    }

    @Nested
    @DisplayName("Statystyki serii")
    class Stats {

        @Test
        @DisplayName("Min, max i średnia pochodzą z tej samej listy punktów, co wykres")
        void statsAreComputedFromTheChartedPoints() {
            TestoPdfReportService.SeriesStats stats =
                    TestoPdfReportService.SeriesStats.of(sampleSeries());

            assertThat(stats.empty()).isFalse();
            assertThat(stats.min()).isEqualTo(2.4);
            assertThat(stats.max()).isEqualTo(8.1);
            assertThat(stats.avg()).isEqualTo(5.125);
        }

        @Test
        @DisplayName("Pierwszy odczyt to punkt otwierający serię, nie moment generowania raportu")
        void firstReadingIsTheOpeningPoint() {
            TestoPdfReportService.SeriesStats stats =
                    TestoPdfReportService.SeriesStats.of(sampleSeries());

            assertThat(stats.first()).isEqualTo(START);
            assertThat(stats.firstReadingFormatted()).isEqualTo("2026-08-05 13:30:00");
        }

        @Test
        @DisplayName("Wartości zmierzone drukują się w rozdzielczości rejestratora, średnia o cyfrę dokładniej")
        void measuredValuesKeepInstrumentResolution() {
            TestoPdfReportService.SeriesStats stats =
                    TestoPdfReportService.SeriesStats.of(sampleSeries());

            // Separator dziesiętny zależy od locale JVM (pl-PL: przecinek, CI en-US: kropka),
            // więc oczekiwanie budujemy tym samym wywołaniem, co kod produkcyjny.
            assertThat(stats.minFormatted()).isEqualTo(String.format("%.1f °C", 2.4));
            assertThat(stats.maxFormatted()).isEqualTo(String.format("%.1f °C", 8.1));
            assertThat(stats.avgFormatted())
                    .as("średnia jest wielkością wyliczoną, więc dostaje dwa miejsca")
                    .isEqualTo(String.format("%.2f °C", 5.125));
        }

        @Test
        @DisplayName("Seria bez punktów daje N/D zamiast zera albo wartości granicznej Double")
        void emptySeriesYieldsNotAvailable() {
            TestoPdfReportService.SeriesStats fromEmpty = TestoPdfReportService.SeriesStats.of(List.of());
            TestoPdfReportService.SeriesStats fromNull = TestoPdfReportService.SeriesStats.of(null);

            for (TestoPdfReportService.SeriesStats stats : List.of(fromEmpty, fromNull)) {
                assertThat(stats.empty()).isTrue();
                assertThat(stats.minFormatted()).isEqualTo("N/D");
                assertThat(stats.maxFormatted()).isEqualTo("N/D");
                assertThat(stats.avgFormatted()).isEqualTo("N/D");
                assertThat(stats.firstReadingFormatted()).isEqualTo("N/D");
            }
        }

        @Test
        @DisplayName("Seria jednopunktowa nie degeneruje statystyk")
        void singlePointSeries() {
            TestoPdfReportService.SeriesStats stats = TestoPdfReportService.SeriesStats.of(
                    List.of(point(1, START, -21.3)));

            assertThat(stats.min()).isEqualTo(-21.3);
            assertThat(stats.max()).isEqualTo(-21.3);
            assertThat(stats.avg()).isEqualTo(-21.3);
            assertThat(stats.first()).isEqualTo(START);
        }

        @Test
        @DisplayName("Ujemne temperatury zamrażarki nie mylą się z wartością początkową maksimum")
        void subZeroSeriesDoesNotBreakMaximum() {
            List<ThermoMeasurementPoint> freezer = List.of(
                    point(1, START, -78.4),
                    point(2, START.plusMinutes(5), -80.1),
                    point(3, START.plusMinutes(10), -79.2));

            TestoPdfReportService.SeriesStats stats = TestoPdfReportService.SeriesStats.of(freezer);

            assertThat(stats.min()).isEqualTo(-80.1);
            assertThat(stats.max())
                    .as("maksimum startujące od 0.0 pokazałoby tu 0 °C dla serii z -80 °C")
                    .isEqualTo(-78.4);
        }
    }

    @Nested
    @DisplayName("Dokument PDF")
    @EnabledOnOs(value = OS.WINDOWS, disabledReason = "Serwis ładuje C:\\Windows\\Fonts\\arial.ttf")
    class Document {

        @Test
        @DisplayName("Metryka niesie charakterystykę serii i czas pierwszego odczytu")
        void metricSectionCarriesSeriesCharacteristics(@TempDir Path tempDir) throws Exception {
            TestoPdfReportService.TestoReportData data = new TestoPdfReportService.TestoReportData();
            data.model = "Testo 174 T";
            data.serialNumber = "SN-TEST-001";
            data.batteryLevel = "386 dni";
            data.interval = "15 minut";
            data.startDelay = "Brak opóźnienia";
            data.comments = null;
            data.measurements = sampleSeries();

            File out = tempDir.resolve("raport.pdf").toFile();
            new TestoPdfReportService().generatePdfReport(data, out, null);

            assertThat(out).exists();
            String page1 = new PdfTextExtractor(new PdfReader(out.getAbsolutePath())).getTextFromPage(1);

            assertThat(page1)
                    .contains("Temperatura min.:")
                    .contains("Temperatura maks.:")
                    .contains("Temperatura średnia:")
                    .contains("Pierwszy odczyt:");

            assertThat(page1)
                    .as("liczby w metryce muszą zgadzać się z serią, dla której powstał wykres")
                    .contains(String.format("%.1f °C", 2.4))
                    .contains(String.format("%.1f °C", 8.1))
                    .contains(String.format("%.2f °C", 5.125))
                    .contains(START.format(DTF));
        }
    }
}