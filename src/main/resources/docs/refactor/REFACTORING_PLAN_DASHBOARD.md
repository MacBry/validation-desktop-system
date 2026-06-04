# Szczegółowy Plan Refaktoryzacji: DashboardController

## Przegląd

Przekształcenie monolitycznego `DashboardController` (437 linii) w orchestrator + 4 specjalistyczne serwisy.

## Architektura Po Refaktoryzacji

```
DashboardController (refactored - ~120 linii)
└── Orchestration i UI initialization

DashboardStatisticsService (150-180 linii)
├── calculateRecorderStatistics()
├── calculateCalibrationStatistics()
├── calculateUserStatistics()
├── calculateDeviceAndChamberStatistics()
└── calculateUsbOperationStatistics()

DashboardChartService (120-150 linii)
├── buildRecordersPieChart()
├── buildCalibrationsPieChart()
├── buildUsersPieChart()
└── buildUsbActivityChart()

AccessLogsTableService (100-120 linii)
├── setupAccessLogsTable()
├── configureLogColumns()
└── loadAndFormatLogs()

DashboardSecurityService (60-80 linii)
├── applyRoleBasedSecurity()
└── isUserAdmin()
```

---

## Krok 1: DTOs dla Statystyk

```java
package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecorderStatistics {
    private long total;
    private long active;
    private long underCalibration;
    private long inactive;
}

@Data
@AllArgsConstructor
public class CalibrationStatistics {
    private long valid;
    private long expiringSoon;
    private long expired;
}

@Data
@AllArgsConstructor
public class UserStatistics {
    private long enabled;
    private long locked;
}

@Data
@AllArgsConstructor
public class DeviceStatistics {
    private long totalDevices;
    private long totalChambers;
    private long validChambers;
    private long warningChambers;
    private long notValidatedChambers;
}

@Data
@AllArgsConstructor
public class UsbStatistics {
    private long totalOperations;
    private long reads;
    private long programs;
}

@Data
@AllArgsConstructor
public class DashboardStatistics {
    private RecorderStatistics recorders;
    private CalibrationStatistics calibrations;
    private UserStatistics users;
    private long departments;
    private long laboratories;
    private DeviceStatistics devices;
    private UsbStatistics usb;
}
```

---

## Krok 2: DashboardStatisticsService

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.*;
import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatisticsService {

    private final ThermoRecorderRepository thermoRecorderRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final CoolingDeviceRepository coolingDeviceRepository;
    private final CoolingChamberRepository coolingChamberRepository;
    private final ThermoMeasurementSeriesRepository thermoMeasurementSeriesRepository;
    private final AccessLogRepository accessLogRepository;

    @Transactional(readOnly = true)
    public DashboardStatistics calculateAllStatistics() {
        return new DashboardStatistics(
            calculateRecorderStatistics(),
            calculateCalibrationStatistics(),
            calculateUserStatistics(),
            departmentRepository.count(),
            laboratoryRepository.count(),
            calculateDeviceAndChamberStatistics(),
            calculateUsbOperationStatistics()
        );
    }

    private RecorderStatistics calculateRecorderStatistics() {
        List<ThermoRecorder> recorders = thermoRecorderRepository.findAll();
        long total = recorders.size();
        long active = recorders.stream().filter(r -> r.getStatus() == RecorderStatus.ACTIVE).count();
        long underCalibration = recorders.stream().filter(r -> r.getStatus() == RecorderStatus.UNDER_CALIBRATION).count();
        long inactive = total - active - underCalibration;

        return new RecorderStatistics(total, active, underCalibration, inactive);
    }

    private CalibrationStatistics calculateCalibrationStatistics() {
        List<ThermoRecorder> recorders = thermoRecorderRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate in30Days = today.plusDays(30);

        long expired = 0;
        long expiringSoon = 0;
        long valid = 0;

        for (ThermoRecorder r : recorders) {
            if (r.getCalibrations() == null || r.getCalibrations().isEmpty()) {
                expired++;
            } else {
                Calibration latest = r.getCalibrations().get(0);
                LocalDate validUntil = latest.getValidUntil();

                if (validUntil == null || validUntil.isBefore(today)) {
                    expired++;
                } else if (validUntil.isBefore(in30Days)) {
                    expiringSoon++;
                } else {
                    valid++;
                }
            }
        }

        return new CalibrationStatistics(valid, expiringSoon, expired);
    }

    private UserStatistics calculateUserStatistics() {
        List<User> users = userRepository.findAll();
        long enabled = users.stream().filter(User::isEnabled).count();
        long locked = users.stream().filter(User::isLocked).count();
        return new UserStatistics(enabled, locked);
    }

    private DeviceStatistics calculateDeviceAndChamberStatistics() {
        long totalDevices = coolingDeviceRepository.count();
        List<CoolingChamber> chambers = coolingChamberRepository.findAll();
        List<ThermoMeasurementSeries> allSeries = thermoMeasurementSeriesRepository.findAll();

        Map<Long, List<ThermoMeasurementSeries>> chamberSeriesMap = new HashMap<>();
        for (ThermoMeasurementSeries series : allSeries) {
            if (series.getCoolingChamber() != null) {
                chamberSeriesMap.computeIfAbsent(series.getCoolingChamber().getId(), k -> new ArrayList<>())
                        .add(series);
            }
        }

        long validChambers = 0;
        long warningChambers = 0;
        long notValidatedChambers = 0;

        for (CoolingChamber chamber : chambers) {
            List<ThermoMeasurementSeries> seriesList = chamberSeriesMap.get(chamber.getId());
            if (seriesList == null || seriesList.isEmpty()) {
                notValidatedChambers++;
            } else {
                boolean hasInvalidCal = seriesList.stream()
                        .anyMatch(s -> {
                            Calibration cal = s.getThermoRecorder().getLatestCalibration();
                            return cal == null || !cal.isValid();
                        });
                if (hasInvalidCal) {
                    warningChambers++;
                } else {
                    validChambers++;
                }
            }
        }

        return new DeviceStatistics(totalDevices, chambers.size(), validChambers, warningChambers, notValidatedChambers);
    }

    private UsbStatistics calculateUsbOperationStatistics() {
        List<AccessLog> usbReads = accessLogRepository.findByActionOrderByTimestampDesc("USB_READING");
        List<AccessLog> usbProgs = accessLogRepository.findByActionOrderByTimestampDesc("USB_PROGRAMMING");
        long total = usbReads.size() + usbProgs.size();
        return new UsbStatistics(total, usbReads.size(), usbProgs.size());
    }
}
```

---

## Krok 3: DashboardChartService

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.*;
import javafx.scene.chart.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardChartService {

    private final AccessLogRepository accessLogRepository;

    public PieChart buildRecordersPieChart(RecorderStatistics stats) {
        PieChart chart = new PieChart();
        chart.getData().addAll(
            new PieChart.Data("Aktywne (" + stats.getActive() + ")", stats.getActive()),
            new PieChart.Data("W kalibracji (" + stats.getUnderCalibration() + ")", stats.getUnderCalibration()),
            new PieChart.Data("Nieaktywne (" + stats.getInactive() + ")", stats.getInactive())
        );
        return chart;
    }

    public PieChart buildCalibrationsPieChart(CalibrationStatistics stats) {
        PieChart chart = new PieChart();
        chart.getData().addAll(
            new PieChart.Data("Ważne (" + stats.getValid() + ")", stats.getValid()),
            new PieChart.Data("Wygasające wkrótce (" + stats.getExpiringSoon() + ")", stats.getExpiringSoon()),
            new PieChart.Data("Przeterminowane (" + stats.getExpired() + ")", stats.getExpired())
        );
        return chart;
    }

    public PieChart buildUsersPieChart(UserStatistics stats) {
        PieChart chart = new PieChart();
        chart.getData().addAll(
            new PieChart.Data("Aktywne (" + stats.getEnabled() + ")", stats.getEnabled()),
            new PieChart.Data("Zablokowane (" + stats.getLocked() + ")", stats.getLocked())
        );
        return chart;
    }

    public BarChart<String, Number> buildUsbActivityChart(List<AccessLog> allLogs) {
        BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());

        XYChart.Series<String, Number> seriesRead = new XYChart.Series<>();
        seriesRead.setName("Pobrane Odczyty");
        
        XYChart.Series<String, Number> seriesProg = new XYChart.Series<>();
        seriesProg.setName("Zaprogramowane");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            String dayLabel = d.format(DateTimeFormatter.ofPattern("dd.MM"));
            
            long readsCount = allLogs.stream()
                    .filter(l -> "USB_READING".equals(l.getAction()))
                    .filter(l -> l.getTimestamp().toLocalDate().equals(d))
                    .count();
            
            long progsCount = allLogs.stream()
                    .filter(l -> "USB_PROGRAMMING".equals(l.getAction()))
                    .filter(l -> l.getTimestamp().toLocalDate().equals(d))
                    .count();
            
            seriesRead.getData().add(new XYChart.Data<>(dayLabel, readsCount));
            seriesProg.getData().add(new XYChart.Data<>(dayLabel, progsCount));
        }

        chart.getData().addAll(seriesRead, seriesProg);
        return chart;
    }
}
```

---

## Krok 4: AccessLogsTableService

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessLogsTableService {

    private final AccessLogRepository accessLogRepository;

    public void setupAccessLogsTable(
            TableView<AccessLog> table,
            TableColumn<AccessLog, String> timestampCol,
            TableColumn<AccessLog, String> usernameCol,
            TableColumn<AccessLog, String> actionCol,
            TableColumn<AccessLog, String> detailsCol) {
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        timestampCol.setCellValueFactory(cellData -> 
                new SimpleStringProperty(cellData.getValue().getTimestamp().format(dtf)));
        usernameCol.setCellValueFactory(cellData -> 
                new SimpleStringProperty(cellData.getValue().getUsername()));
        actionCol.setCellValueFactory(cellData -> 
                new SimpleStringProperty(cellData.getValue().getAction()));
        detailsCol.setCellValueFactory(cellData -> 
                new SimpleStringProperty(cellData.getValue().getDetails()));

        table.setRowFactory(tv -> new TableRow<AccessLog>() {
            @Override
            protected void updateItem(AccessLog item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    setStyle(getStyleForAction(item.getAction()));
                }
            }
        });

        loadAndFormatLogs(table, 5);
    }

    public void loadAndFormatLogs(TableView<AccessLog> table, int limit) {
        List<AccessLog> logs = accessLogRepository.findTop100ByOrderByTimestampDesc()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
        
        table.setItems(FXCollections.observableArrayList(logs));
    }

    private String getStyleForAction(String action) {
        if ("USB_PROGRAMMING".equals(action)) {
            return "-fx-background-color: #f3e8ff;";
        } else if ("USB_READING".equals(action)) {
            return "-fx-background-color: #ecfdf5;";
        } else if (action != null && action.contains("FAILED")) {
            return "-fx-background-color: #fee2e2;";
        }
        return "";
    }
}
```

---

## Krok 5: DashboardSecurityService

```java
package com.mac.bry.desktop.service;

import javafx.scene.chart.BarChart;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardSecurityService {

    public boolean isUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_DEPT_ADMIN"));
    }

    public void applyRoleBasedVisibility(
            VBox usersCard,
            VBox structureCard,
            VBox testoCard,
            VBox usersChartBox,
            GridPane chartsGrid,
            VBox logsBox,
            BarChart<String, Number> usbActivityChart) {
        
        if (!isUserAdmin()) {
            log.info("Applying role-based visibility filters for non-admin user");
            
            hideElement(usersCard);
            hideElement(structureCard);

            if (testoCard != null) {
                GridPane.setColumnIndex(testoCard, 0);
                GridPane.setColumnSpan(testoCard, 4);
            }

            hideElement(usersChartBox);
            
            if (chartsGrid != null) {
                chartsGrid.getColumnConstraints().clear();
                
                ColumnConstraints cc1 = new ColumnConstraints();
                cc1.setPercentWidth(50.0);
                ColumnConstraints cc2 = new ColumnConstraints();
                cc2.setPercentWidth(50.0);
                
                chartsGrid.getColumnConstraints().addAll(cc1, cc2);

                if (usbActivityChart != null && usbActivityChart.getParent() != null) {
                    GridPane.setColumnSpan(usbActivityChart.getParent(), 2);
                }
            }

            hideElement(logsBox);
        }
    }

    private void hideElement(VBox element) {
        if (element != null) {
            element.setVisible(false);
            element.setManaged(false);
        }
    }
}
```

---

## Krok 6: Refactored DashboardController

```java
package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.DashboardStatistics;
import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import com.mac.bry.desktop.service.*;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardStatisticsService statisticsService;
    private final DashboardChartService chartService;
    private final AccessLogsTableService logsTableService;
    private final DashboardSecurityService securityService;
    private final AccessLogRepository accessLogRepository;

    // Header
    @FXML private Label welcomeLabel, roleLabel, dateLabel, timeLabel;

    // Statistics
    @FXML private Label totalRecordersLabel, activeRecordersLabel, calibRecordersLabel;
    @FXML private Label expiredCalibrationsLabel, expiringSoonLabel, validCalibrationsLabel;
    @FXML private Label totalUsersLabel, lockedUsersLabel;
    @FXML private Label totalDepartmentsLabel, totalLaboratoriesLabel;
    @FXML private Label totalDevicesLabel, totalChambersLabel, validChambersLabel, warningChambersLabel, notValidatedChambersLabel;
    @FXML private Label totalUsbOperationsLabel, usbReadLabel, usbProgramLabel;

    // Charts
    @FXML private PieChart recordersPieChart, calibrationsPieChart, usersPieChart;
    @FXML private BarChart<String, Number> usbActivityChart;

    // Layout
    @FXML private GridPane cardsGrid;
    @FXML private VBox usersCard, structureCard, testoCard, usersChartBox, logsBox;
    @FXML private GridPane chartsGrid;
    @FXML private HBox gxpAlertBox;
    @FXML private Label gxpAlertMessage;

    // Logs table
    @FXML private TableView<AccessLog> accessLogsTable;
    @FXML private TableColumn<AccessLog, String> logTimestampColumn, logUsernameColumn, logActionColumn, logDetailsColumn;

    @FXML
    public void initialize() {
        log.info("Initializing DashboardController");
        setupHeader();
        setupAndLoadStatistics();
        logsTableService.setupAccessLogsTable(accessLogsTable, logTimestampColumn, logUsernameColumn, 
                                              logActionColumn, logDetailsColumn);
        securityService.applyRoleBasedVisibility(usersCard, structureCard, testoCard, usersChartBox, 
                                                  chartsGrid, logsBox, usbActivityChart);
        log.info("DashboardController initialization completed");
    }

    private void setupHeader() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                welcomeLabel.setText("Witaj w systemie, " + user.getFullName() + "!");
            } else {
                welcomeLabel.setText("Witaj w systemie, " + auth.getName() + "!");
            }

            String roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.replace("ROLE_", ""))
                    .collect(Collectors.joining(", "));
            roleLabel.setText("Uprawnienia: " + (roles.isEmpty() ? "Brak" : roles));
        }

        LocalDate now = LocalDate.now();
        Locale plLocale = new Locale("pl");
        dateLabel.setText(now.format(DateTimeFormatter.ofPattern("d MMMM yyyy", plLocale)));
        timeLabel.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    private void setupAndLoadStatistics() {
        try {
            DashboardStatistics stats = statisticsService.calculateAllStatistics();
            updateStatisticsLabels(stats);
            buildCharts(stats);
            updateGxPAlert(stats);
        } catch (Exception e) {
            log.error("Error calculating dashboard statistics", e);
        }
    }

    private void updateStatisticsLabels(DashboardStatistics stats) {
        totalRecordersLabel.setText(String.valueOf(stats.getRecorders().getTotal()));
        activeRecordersLabel.setText(String.valueOf(stats.getRecorders().getActive()));
        calibRecordersLabel.setText(String.valueOf(stats.getRecorders().getUnderCalibration()));

        expiredCalibrationsLabel.setText(String.valueOf(stats.getCalibrations().getExpired()));
        expiringSoonLabel.setText(String.valueOf(stats.getCalibrations().getExpiringSoon()));
        validCalibrationsLabel.setText(String.valueOf(stats.getCalibrations().getValid()));

        totalUsersLabel.setText(String.valueOf(stats.getUsers().getEnabled()));
        lockedUsersLabel.setText(String.valueOf(stats.getUsers().getLocked()));

        totalDepartmentsLabel.setText(String.valueOf(stats.getDepartments()));
        totalLaboratoriesLabel.setText(String.valueOf(stats.getLaboratories()));

        if (totalDevicesLabel != null) totalDevicesLabel.setText(String.valueOf(stats.getDevices().getTotalDevices()));
        if (totalChambersLabel != null) totalChambersLabel.setText(String.valueOf(stats.getDevices().getTotalChambers()));
        if (validChambersLabel != null) validChambersLabel.setText(String.valueOf(stats.getDevices().getValidChambers()));
        if (warningChambersLabel != null) warningChambersLabel.setText(String.valueOf(stats.getDevices().getWarningChambers()));
        if (notValidatedChambersLabel != null) notValidatedChambersLabel.setText(String.valueOf(stats.getDevices().getNotValidatedChambers()));

        if (usbReadLabel != null) usbReadLabel.setText(String.valueOf(stats.getUsb().getReads()));
        if (usbProgramLabel != null) usbProgramLabel.setText(String.valueOf(stats.getUsb().getPrograms()));
        if (totalUsbOperationsLabel != null) totalUsbOperationsLabel.setText(String.valueOf(stats.getUsb().getTotalOperations()));
    }

    private void buildCharts(DashboardStatistics stats) {
        recordersPieChart.getData().clear();
        recordersPieChart.setData(chartService.buildRecordersPieChart(stats.getRecorders()).getData());

        calibrationsPieChart.getData().clear();
        calibrationsPieChart.setData(chartService.buildCalibrationsPieChart(stats.getCalibrations()).getData());

        usersPieChart.getData().clear();
        usersPieChart.setData(chartService.buildUsersPieChart(stats.getUsers()).getData());

        if (usbActivityChart != null) {
            usbActivityChart.getData().clear();
            List<AccessLog> allLogs = accessLogRepository.findAll();
            usbActivityChart.setData(chartService.buildUsbActivityChart(allLogs).getData());
        }
    }

    private void updateGxPAlert(DashboardStatistics stats) {
        if (stats.getCalibrations().getExpired() > 0 || 
            stats.getCalibrations().getExpiringSoon() > 0 || 
            stats.getDevices().getWarningChambers() > 0) {
            
            if (gxpAlertBox != null) {
                gxpAlertBox.setVisible(true);
                gxpAlertBox.setManaged(true);
                StringBuilder msg = new StringBuilder("Wymagana uwaga! System wykrył: ");
                if (stats.getCalibrations().getExpired() > 0) {
                    msg.append(String.format("%d przeterminowanych wzorcowań. ", stats.getCalibrations().getExpired()));
                }
                if (stats.getCalibrations().getExpiringSoon() > 0) {
                    msg.append(String.format("%d wygasających do 30 dni. ", stats.getCalibrations().getExpiringSoon()));
                }
                if (stats.getDevices().getWarningChambers() > 0) {
                    msg.append(String.format("%d komór z ostrzeżeniem GxP. ", stats.getDevices().getWarningChambers()));
                }
                msg.append("Przeprowadź Procedurę Rewalidacji GxP.");
                gxpAlertMessage.setText(msg.toString());
            }
        } else {
            if (gxpAlertBox != null) {
                gxpAlertBox.setVisible(false);
                gxpAlertBox.setManaged(false);
            }
        }
    }
}
```

---

## Podsumowanie Zmian

| Aspekt | Przed | Po | Poprawa |
|--------|-------|----|----|
| **Rozmiar DashboardController** | 437 linii | ~120 linii | -73% |
| **Liczba serwisów** | 0 | 4 | +400% testability |
| **Odpowiedzialności** | 10+ | 1-2 | SRP spełniony |
| **Repositories w kontrolerze** | 8 | 1-2 | Czyszczość kodu |
| **Testability** | Trudna (UI logic mixed with business) | Łatwa (services separately testable) | ✅ |

---

## Checklistę Implementacji

- [ ] Stworzyć DTOs dla statystyk
- [ ] Stworzyć DashboardStatisticsService
- [ ] Stworzyć DashboardChartService
- [ ] Stworzyć AccessLogsTableService
- [ ] Stworzyć DashboardSecurityService
- [ ] Refactoryzować DashboardController
- [ ] Uruchomić testy integracyjne
- [ ] Zweryfikować UI w aplikacji
- [ ] Usunąć stare metody z DashboardController

