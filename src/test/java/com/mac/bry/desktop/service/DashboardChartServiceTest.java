package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.CalibrationStatistics;
import com.mac.bry.desktop.dto.ChartSeries;
import com.mac.bry.desktop.dto.RecorderStatistics;
import com.mac.bry.desktop.dto.UserStatistics;
import com.mac.bry.desktop.security.model.AccessLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardChartServiceTest {

    private final DashboardChartService chartService = new DashboardChartService();

    @Nested
    @DisplayName("Recorders Pie Chart Data Tests")
    class RecordersPieChartTests {
        @Test
        @DisplayName("Should format recorders statistics correctly")
        void shouldFormatRecordersStatistics() {
            RecorderStatistics stats = new RecorderStatistics(10, 5, 3, 2);

            Map<String, Number> data = chartService.getRecordersPieChartData(stats);

            assertThat(data)
                    .hasSize(3)
                    .containsEntry("Aktywne (5)", 5L)
                    .containsEntry("W kalibracji (3)", 3L)
                    .containsEntry("Nieaktywne (2)", 2L);
        }
    }

    @Nested
    @DisplayName("Calibrations Pie Chart Data Tests")
    class CalibrationsPieChartTests {
        @Test
        @DisplayName("Should format calibrations statistics correctly")
        void shouldFormatCalibrationsStatistics() {
            CalibrationStatistics stats = new CalibrationStatistics(8, 4, 2);

            Map<String, Number> data = chartService.getCalibrationsPieChartData(stats);

            assertThat(data)
                    .hasSize(3)
                    .containsEntry("Ważne (8)", 8L)
                    .containsEntry("Wygasające wkrótce (4)", 4L)
                    .containsEntry("Przeterminowane (2)", 2L);
        }
    }

    @Nested
    @DisplayName("Users Pie Chart Data Tests")
    class UsersPieChartTests {
        @Test
        @DisplayName("Should format users statistics correctly")
        void shouldFormatUsersStatistics() {
            UserStatistics stats = new UserStatistics(12, 3);

            Map<String, Number> data = chartService.getUsersPieChartData(stats);

            assertThat(data)
                    .hasSize(2)
                    .containsEntry("Aktywne (12)", 12L)
                    .containsEntry("Zablokowane (3)", 3L);
        }
    }

    @Nested
    @DisplayName("USB Activity Chart Data Tests")
    class UsbActivityChartTests {
        @Test
        @DisplayName("Should map USB logs into chart series for the last 7 days")
        void shouldMapUsbLogs() {
            // Create logs on different days
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDate twoDaysAgo = today.minusDays(2);

            AccessLog log1 = new AccessLog();
            log1.setAction("USB_READING");
            log1.setTimestamp(today.atTime(10, 0));

            AccessLog log2 = new AccessLog();
            log2.setAction("USB_READING");
            log2.setTimestamp(yesterday.atTime(14, 0));

            AccessLog log3 = new AccessLog();
            log3.setAction("USB_PROGRAMMING");
            log3.setTimestamp(yesterday.atTime(15, 0));

            AccessLog log4 = new AccessLog();
            log4.setAction("USB_PROGRAMMING");
            log4.setTimestamp(twoDaysAgo.atTime(11, 0));

            AccessLog log5 = new AccessLog();
            log5.setAction("PDF_IMPORT");
            log5.setTimestamp(today.atTime(12, 0));

            List<AccessLog> allLogs = List.of(log1, log2, log3, log4, log5);

            List<ChartSeries> chartData = chartService.getUsbActivityChartData(allLogs);

            assertThat(chartData).hasSize(2);

            ChartSeries readsSeries = chartData.get(0);
            assertThat(readsSeries.name()).isEqualTo("Pobrane Odczyty");
            
            ChartSeries progsSeries = chartData.get(1);
            assertThat(progsSeries.name()).isEqualTo("Zaprogramowane");

            // Format day labels to match service implementation
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");
            String todayLabel = today.format(formatter);
            String yesterdayLabel = yesterday.format(formatter);
            String twoDaysAgoLabel = twoDaysAgo.format(formatter);

            assertThat(readsSeries.dataPoints())
                    .containsEntry(todayLabel, 2L)
                    .containsEntry(yesterdayLabel, 1L)
                    .containsEntry(twoDaysAgoLabel, 0L);

            assertThat(progsSeries.dataPoints())
                    .containsEntry(todayLabel, 0L)
                    .containsEntry(yesterdayLabel, 1L)
                    .containsEntry(twoDaysAgoLabel, 1L);
        }
    }
}
