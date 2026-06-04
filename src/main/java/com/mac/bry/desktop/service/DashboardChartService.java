package com.mac.bry.desktop.service;

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
public class DashboardChartService {

    public Map<String, Number> getRecordersPieChartData(RecorderStatistics stats) {
        Map<String, Number> data = new LinkedHashMap<>();
        data.put("Aktywne (" + stats.getActive() + ")", stats.getActive());
        data.put("W kalibracji (" + stats.getUnderCalibration() + ")", stats.getUnderCalibration());
        data.put("Nieaktywne (" + stats.getInactive() + ")", stats.getInactive());
        log.debug("Generated recorders pie chart data");
        return data;
    }

    public Map<String, Number> getCalibrationsPieChartData(CalibrationStatistics stats) {
        Map<String, Number> data = new LinkedHashMap<>();
        data.put("Ważne (" + stats.getValid() + ")", stats.getValid());
        data.put("Wygasające wkrótce (" + stats.getExpiringSoon() + ")", stats.getExpiringSoon());
        data.put("Przeterminowane (" + stats.getExpired() + ")", stats.getExpired());
        log.debug("Generated calibrations pie chart data");
        return data;
    }

    public Map<String, Number> getUsersPieChartData(UserStatistics stats) {
        Map<String, Number> data = new LinkedHashMap<>();
        data.put("Aktywne (" + stats.getEnabled() + ")", stats.getEnabled());
        data.put("Zablokowane (" + stats.getLocked() + ")", stats.getLocked());
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
            new ChartSeries("Pobrane Odczyty", readPoints),
            new ChartSeries("Zaprogramowane", progPoints)
        );
    }
}

