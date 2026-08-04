package com.mac.bry.desktop.service;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.dto.CalibrationStatistics;
import com.mac.bry.desktop.dto.ChartSeries;
import com.mac.bry.desktop.dto.RecorderStatistics;
import com.mac.bry.desktop.dto.UserStatistics;
import com.mac.bry.desktop.security.model.AccessLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * Buduje dane wykresów dashboardu.
 * <p>
 * Etykiety są tekstem UI, nie treścią raportu GxP — JavaFX bierze je z modelu
 * danych ({@code PieChart.Data}, {@code XYChart.Series#setName}), a nie z FXML,
 * więc nie da się ich przetłumaczyć kluczem {@code %...}. Stąd {@link I18n#t}
 * tutaj, mimo że to warstwa serwisowa.
 */
public class DashboardChartService {

    public Map<String, Number> getRecordersPieChartData(RecorderStatistics stats) {
        Map<String, Number> data = new LinkedHashMap<>();
        data.put(I18n.t("dashboard.chart.recorders.active", stats.getActive()), stats.getActive());
        data.put(I18n.t("dashboard.chart.recorders.inCalibration", stats.getUnderCalibration()), stats.getUnderCalibration());
        data.put(I18n.t("dashboard.chart.recorders.inactive", stats.getInactive()), stats.getInactive());
        log.debug("Generated recorders pie chart data");
        return data;
    }

    public Map<String, Number> getCalibrationsPieChartData(CalibrationStatistics stats) {
        Map<String, Number> data = new LinkedHashMap<>();
        data.put(I18n.t("dashboard.chart.calibrations.valid", stats.getValid()), stats.getValid());
        data.put(I18n.t("dashboard.chart.calibrations.expiringSoon", stats.getExpiringSoon()), stats.getExpiringSoon());
        data.put(I18n.t("dashboard.chart.calibrations.expired", stats.getExpired()), stats.getExpired());
        log.debug("Generated calibrations pie chart data");
        return data;
    }

    public Map<String, Number> getUsersPieChartData(UserStatistics stats) {
        Map<String, Number> data = new LinkedHashMap<>();
        data.put(I18n.t("dashboard.chart.users.enabled", stats.getEnabled()), stats.getEnabled());
        data.put(I18n.t("dashboard.chart.users.locked", stats.getLocked()), stats.getLocked());
        log.debug("Generated users pie chart data");
        return data;
    }

    public List<ChartSeries> getUsbActivityChartData(List<AccessLog> allLogs) {
        Map<String, Number> readPoints = new LinkedHashMap<>();
        Map<String, Number> progPoints = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            String dayLabel = d.format(DateTimeFormatter.ofPattern("dd.MM"));

            long readsCount = allLogs.stream()
                    .filter(l -> "USB_READING".equals(l.getAction()) || "PDF_IMPORT".equals(l.getAction()))
                    .filter(l -> l.getTimestamp().toLocalDate().equals(d))
                    .count();

            long progsCount = allLogs.stream()
                    .filter(l -> "USB_PROGRAMMING".equals(l.getAction()))
                    .filter(l -> l.getTimestamp().toLocalDate().equals(d))
                    .count();

            readPoints.put(dayLabel, readsCount);
            progPoints.put(dayLabel, progsCount);
        }

        log.debug("Generated USB activity chart data");
        return List.of(
            new ChartSeries(I18n.t("dashboard.chart.usb.reads"), readPoints),
            new ChartSeries(I18n.t("dashboard.chart.usb.programs"), progPoints)
        );
    }
}

