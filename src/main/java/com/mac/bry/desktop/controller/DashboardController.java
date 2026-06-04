package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.controller.helper.AccessLogsTableHelper;
import com.mac.bry.desktop.dto.ChartSeries;
import com.mac.bry.desktop.dto.DashboardStatistics;
import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.service.AccessLogService;
import com.mac.bry.desktop.service.DashboardChartService;
import com.mac.bry.desktop.service.DashboardSecurityService;
import com.mac.bry.desktop.service.DashboardStatisticsService;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
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
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardStatisticsService statisticsService;
    private final DashboardChartService chartService;
    private final AccessLogService accessLogService;
    private final DashboardSecurityService securityService;

    @FXML private Label welcomeLabel, roleLabel, dateLabel, timeLabel;
    @FXML private Label totalRecordersLabel, activeRecordersLabel, calibRecordersLabel;
    @FXML private Label expiredCalibrationsLabel, expiringSoonLabel, validCalibrationsLabel;
    @FXML private Label totalUsersLabel, lockedUsersLabel;
    @FXML private Label totalDepartmentsLabel, totalLaboratoriesLabel;
    @FXML private Label totalDevicesLabel, totalChambersLabel, validChambersLabel, warningChambersLabel, notValidatedChambersLabel;
    @FXML private Label totalUsbOperationsLabel, usbReadLabel, usbProgramLabel;

    @FXML private PieChart recordersPieChart, calibrationsPieChart, usersPieChart;
    @FXML private BarChart<String, Number> usbActivityChart;

    @FXML private GridPane cardsGrid;
    @FXML private VBox usersCard, structureCard, testoCard, usersChartBox, logsBox;
    @FXML private GridPane chartsGrid;
    @FXML private HBox gxpAlertBox;
    @FXML private Label gxpAlertMessage;

    @FXML private TableView<AccessLog> accessLogsTable;
    @FXML private TableColumn<AccessLog, String> logTimestampColumn, logUsernameColumn, logActionColumn, logDetailsColumn;

    @FXML
    public void initialize() {
        log.info("Initializing DashboardController");
        setupHeader();
        setupAndLoadStatistics();
        AccessLogsTableHelper.setupAccessLogsTable(accessLogsTable, logTimestampColumn, logUsernameColumn,
                                                   logActionColumn, logDetailsColumn);
        applyRoleBasedVisibility();
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
            
            // Load logs data and populate table
            List<AccessLog> recentLogs = accessLogService.getRecentLogs(5);
            AccessLogsTableHelper.populateLogs(accessLogsTable, recentLogs);
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
        chartService.getRecordersPieChartData(stats.getRecorders()).forEach((key, value) -> 
            recordersPieChart.getData().add(new PieChart.Data(key, value.doubleValue()))
        );

        calibrationsPieChart.getData().clear();
        chartService.getCalibrationsPieChartData(stats.getCalibrations()).forEach((key, value) -> 
            calibrationsPieChart.getData().add(new PieChart.Data(key, value.doubleValue()))
        );

        usersPieChart.getData().clear();
        chartService.getUsersPieChartData(stats.getUsers()).forEach((key, value) -> 
            usersPieChart.getData().add(new PieChart.Data(key, value.doubleValue()))
        );

        if (usbActivityChart != null) {
            usbActivityChart.getData().clear();
            List<AccessLog> allLogs = accessLogService.getAllLogs();
            List<ChartSeries> seriesList = chartService.getUsbActivityChartData(allLogs);
            for (ChartSeries series : seriesList) {
                XYChart.Series<String, Number> fxSeries = new XYChart.Series<>();
                fxSeries.setName(series.name());
                series.dataPoints().forEach((k, v) -> fxSeries.getData().add(new XYChart.Data<>(k, v)));
                usbActivityChart.getData().add(fxSeries);
            }
        }
    }

    private void applyRoleBasedVisibility() {
        if (!securityService.isUserAdmin()) {
            log.info("Applying role-based visibility filters for non-admin user");

            if (usersCard != null) {
                usersCard.setVisible(false);
                usersCard.setManaged(false);
            }
            if (structureCard != null) {
                structureCard.setVisible(false);
                structureCard.setManaged(false);
            }

            if (testoCard != null) {
                GridPane.setColumnIndex(testoCard, 0);
                GridPane.setColumnSpan(testoCard, 4);
            }

            if (usersChartBox != null) {
                usersChartBox.setVisible(false);
                usersChartBox.setManaged(false);
            }

            if (chartsGrid != null) {
                chartsGrid.getColumnConstraints().clear();

                javafx.scene.layout.ColumnConstraints cc1 = new javafx.scene.layout.ColumnConstraints();
                cc1.setPercentWidth(50.0);
                javafx.scene.layout.ColumnConstraints cc2 = new javafx.scene.layout.ColumnConstraints();
                cc2.setPercentWidth(50.0);

                chartsGrid.getColumnConstraints().addAll(cc1, cc2);

                if (usbActivityChart != null && usbActivityChart.getParent() != null) {
                    GridPane.setColumnSpan(usbActivityChart.getParent(), 2);
                }
            }

            if (logsBox != null) {
                logsBox.setVisible(false);
                logsBox.setManaged(false);
            }
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

