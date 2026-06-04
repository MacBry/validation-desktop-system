package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GxPProcedureServiceTest {

    private ThermoMeasurementSeriesRepository seriesRepository;
    private GxPProcedureService procedureService;

    @BeforeEach
    void setUp() {
        seriesRepository = Mockito.mock(ThermoMeasurementSeriesRepository.class);
        procedureService = new GxPProcedureService(seriesRepository);
    }

    @Test
    @DisplayName("should group series by revalidationGroupId and generate ProcedureRows")
    void shouldGroupSeriesAndGenerateProcedures() {
        // Arrange
        CoolingDevice device = CoolingDevice.builder().id(1L).name("Fridge A").inventoryNumber("INV-001").build();
        CoolingChamber chamber = CoolingChamber.builder().id(1L).chamberName("Chamber 1").coolingDevice(device).build();
        
        Calibration validCal = Calibration.builder().validUntil(LocalDate.now().plusDays(30)).build();
        ThermoRecorder recorder1 = ThermoRecorder.builder().serialNumber("SN001").calibrations(new ArrayList<>(List.of(validCal))).build();
        ThermoRecorder recorder2 = ThermoRecorder.builder().serialNumber("SN002").calibrations(new ArrayList<>(List.of(validCal))).build();
        
        ThermoMeasurementSeries series1 = ThermoMeasurementSeries.builder()
                .id(1L)
                .revalidationGroupId("group-1")
                .coolingChamber(chamber)
                .thermoRecorder(recorder1)
                .measurementsCount(100)
                .importedAt(LocalDateTime.of(2026, 5, 21, 10, 0))
                .gridPosition(RevalidationSession.GridPosition.TOP_FRONT_LEFT)
                .build();
        
        ThermoMeasurementSeries series2 = ThermoMeasurementSeries.builder()
                .id(2L)
                .revalidationGroupId("group-1")
                .coolingChamber(chamber)
                .thermoRecorder(recorder2)
                .measurementsCount(120)
                .importedAt(LocalDateTime.of(2026, 5, 21, 10, 5))
                .gridPosition(RevalidationSession.GridPosition.BOTTOM_FRONT_LEFT)
                .build();

        when(seriesRepository.findAll()).thenReturn(List.of(series1, series2));

        // Act
        List<ProcedureRow> procedures = procedureService.loadProcedures();

        // Assert
        assertThat(procedures).hasSize(1);
        ProcedureRow row = procedures.get(0);
        assertThat(row.getType()).isEqualTo("Rewalidacja Komory (Kwalifikacja 2-Kanałowa)");
        assertThat(row.getLocation()).isEqualTo("Fridge A [INV-001] / Chamber 1");
        assertThat(row.getDateImported()).isEqualTo("2026-05-21 10:05");
        assertThat(row.getSensors()).isEqualTo("SN001, SN002"); // sorted by gridPosition
        assertThat(row.getMeasurementsCount()).isEqualTo(220);
        assertThat(row.getGxpStatus()).isEqualTo("ZATWIERDZONA GxP");
        assertThat(row.getDevice()).isEqualTo(device);
        assertThat(row.getChamber()).isEqualTo(chamber);
    }

    @Test
    @DisplayName("should set status to GxP Warning if any recorder has expired/missing calibration")
    void shouldSetWarningWhenCalibrationInvalid() {
        // Arrange
        CoolingDevice device = CoolingDevice.builder().id(1L).name("Fridge A").inventoryNumber("INV-001").build();
        CoolingChamber chamber = CoolingChamber.builder().id(1L).chamberName("Chamber 1").coolingDevice(device).build();
        
        Calibration expiredCal = Calibration.builder().validUntil(LocalDate.now().minusDays(1)).build();
        ThermoRecorder recorder = ThermoRecorder.builder().serialNumber("SN001").calibrations(new ArrayList<>(List.of(expiredCal))).build();
        
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .id(1L)
                .revalidationGroupId("group-2")
                .coolingChamber(chamber)
                .thermoRecorder(recorder)
                .measurementsCount(50)
                .importedAt(LocalDateTime.of(2026, 5, 21, 11, 0))
                .build();

        when(seriesRepository.findAll()).thenReturn(List.of(series));

        // Act
        List<ProcedureRow> procedures = procedureService.loadProcedures();

        // Assert
        assertThat(procedures).hasSize(1);
        assertThat(procedures.get(0).getGxpStatus()).isEqualTo("OSTRZEŻENIE GxP");
    }

    @Test
    @DisplayName("should map associated series to DetailRow lists correctly")
    void shouldMapDetailRows() {
        // Arrange
        ThermoRecorder recorder = ThermoRecorder.builder().serialNumber("SN999").build();
        ThermoMeasurementSeries s = ThermoMeasurementSeries.builder()
                .thermoRecorder(recorder)
                .gridPosition(RevalidationSession.GridPosition.TOP_FRONT_LEFT)
                .minTemperature(2.5)
                .maxTemperature(8.2)
                .avgTemperature(5.1)
                .mktTemperature(5.3)
                .expandedUncertainty(0.123)
                .spikeCount(2)
                .driftClassification("DRIFT")
                .build();

        // Act
        List<DetailRow> details = procedureService.loadDetailRows(List.of(s));

        // Assert
        assertThat(details).hasSize(1);
        DetailRow d = details.get(0);
        assertThat(d.getPositionName()).isEqualTo("Góra - Przód-Lewy");
        assertThat(d.getSerialNumber()).isEqualTo("SN999");
        assertThat(d.getMinTemp()).isEqualTo(String.format("%.1f°C", 2.5));
        assertThat(d.getMaxTemp()).isEqualTo(String.format("%.1f°C", 8.2));
        assertThat(d.getAvgTemp()).isEqualTo(String.format("%.1f°C", 5.1));
        assertThat(d.getMktTemp()).isEqualTo(String.format("%.1f°C", 5.3));
        assertThat(d.getUncertainty()).isEqualTo(String.format("±%.3f°C", 0.123));
        assertThat(d.getSpikes()).isEqualTo("2");
        assertThat(d.getDriftClassification()).isEqualTo("DRIFT");
    }
}
