package com.mac.bry.desktop.service;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.dto.RecorderDetailProperty;
import com.mac.bry.desktop.dto.RecorderReadoutSummary;
import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.repository.CalibrationRepository;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import com.mac.bry.desktop.security.model.Department;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecorderDetailsServiceTest {

    @Mock private ThermoRecorderService recorderService;
    @Mock private CalibrationRepository calibrationRepository;
    @Mock private ThermoMeasurementSeriesRepository seriesRepository;

    @InjectMocks private RecorderDetailsService detailsService;

    @BeforeEach
    void setUp() {
        I18n.init("pl");
        when(recorderService.getCalibrationStatus(any())).thenReturn("WAŻNE DO 2027-01-01");
        when(calibrationRepository.findByThermoRecorderIdOrderByCalibrationDateDesc(anyLong()))
                .thenReturn(List.of());
        when(seriesRepository.countByThermoRecorderId(anyLong())).thenReturn(0L);
        when(seriesRepository.findReadoutSummaries(anyLong(), any())).thenReturn(List.of());
    }

    @AfterEach
    void resetLocale() {
        I18n.init("pl");
    }

    private ThermoRecorderModel replaceableModel() {
        return ThermoRecorderModel.builder()
                .name("testo 174 T")
                .channelCount(1)
                .sampleCapacity(16000)
                .minOperatingTempC(-30.0)
                .maxOperatingTempC(70.0)
                .batteryType("CR2032")
                .batteryReplaceable(true)
                .batteryLifeDays(500)
                .batteryLifeRefCycleMin(15)
                .batteryLifeRefTempC(25.0)
                .batteryShelfLifeMonths(24)
                .build();
    }

    private ThermoRecorder recorderWith(ThermoRecorderModel model) {
        Department dept = new Department();
        dept.setName("Dział Kontroli Jakości");
        return ThermoRecorder.builder()
                .id(1L)
                .serialNumber("SN-001")
                .model(model)
                .status(RecorderStatus.ACTIVE)
                .resolution(new BigDecimal("0.100"))
                .department(dept)
                .build();
    }

    private String valueOf(List<RecorderDetailProperty> rows, String label) {
        return rows.stream()
                .filter(r -> r.label().equals(label))
                .map(RecorderDetailProperty::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Brak wiersza: " + label));
    }

    @Test
    @DisplayName("Powinien wypełnić wszystkie sekcje karty")
    void shouldBuildAllSections() {
        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(replaceableModel()));

        assertThat(rows).extracting(RecorderDetailProperty::section).containsSubsequence(
                "Identyfikacja", "Model — dane katalogowe", "Bateria i czas pracy",
                "Wzorcowanie", "Historia odczytów");
        assertThat(valueOf(rows, "Numer seryjny")).isEqualTo("SN-001");
        assertThat(valueOf(rows, "Zakres pracy")).isEqualTo("-30,0…70,0 °C");
        assertThat(valueOf(rows, "Pamięć na kanał [odczyty]")).isEqualTo("16000");
    }

    @Test
    @DisplayName("Brak danych powinien być jawnym tekstem, nie pustą komórką")
    void shouldMarkMissingDataExplicitly() {
        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(replaceableModel()));

        // Rejestrator bez odczytu baterii — reguła W4c nie ma z czego liczyć budżetu.
        assertThat(valueOf(rows, "Ostatni odczytany poziom baterii")).isEqualTo("— brak danych");
        assertThat(valueOf(rows, "Szacowany czas pracy")).isEqualTo("— brak danych");
        assertThat(valueOf(rows, "Pracownia")).isEqualTo("— brak danych");
        assertThat(rows).extracting(RecorderDetailProperty::value).doesNotContainNull();
    }

    @Test
    @DisplayName("Budżet pracy baterii wymiennej powinien skalować żywotność katalogową stanem naładowania")
    void shouldScaleRuntimeByStateOfCharge() {
        ThermoRecorder recorder = recorderWith(replaceableModel());
        recorder.setLastBatteryLevelPercent(50);

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorder);

        // 500 dni katalogowo × 50% naładowania = 250 dni przy cyklu referencyjnym.
        assertThat(valueOf(rows, "Szacowany czas pracy")).isEqualTo("≈ 250,0 dni przy cyklu referencyjnym 15 min");
    }

    @Test
    @DisplayName("Logger jednorazowy powinien odliczać limit pracy od pierwszego uruchomienia")
    void shouldCountDownDisposableLoggerLimit() {
        ThermoRecorderModel disposable = ThermoRecorderModel.builder()
                .name("testo 184 T1")
                .channelCount(1)
                .sampleCapacity(16000)
                .minOperatingTempC(-35.0)
                .maxOperatingTempC(70.0)
                .batteryReplaceable(false)
                .operatingDurationDays(90)
                .build();
        ThermoRecorder recorder = recorderWith(disposable);
        recorder.setFirstActivationDate(LocalDate.now().minusDays(30));

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorder);

        assertThat(valueOf(rows, "Szacowany czas pracy")).isEqualTo("≈ 60 dni z limitu 90 dni");
        // Kartoteka jednorazowego nie ma czego pokazywać w polu żywotności katalogowej.
        assertThat(rows).extracting(RecorderDetailProperty::label)
                .contains("Limit pracy urządzenia [dni]")
                .doesNotContain("Żywotność katalogowa");
    }

    @Test
    @DisplayName("Nieuruchomiony logger jednorazowy powinien pokazywać pełny limit")
    void shouldReportFullLimitForUnusedDisposableLogger() {
        ThermoRecorderModel disposable = ThermoRecorderModel.builder()
                .name("testo 184 T2")
                .channelCount(1)
                .batteryReplaceable(false)
                .operatingDurationDays(150)
                .build();

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(disposable));

        assertThat(valueOf(rows, "Szacowany czas pracy"))
                .isEqualTo("pełny limit 150 dni — urządzenie nieuruchomione");
    }

    @Test
    @DisplayName("Przeterminowane ogniwo powinno być oznaczone, a nie podane samą datą")
    void shouldFlagExpiredBatteryCell() {
        ThermoRecorder recorder = recorderWith(replaceableModel());
        // Wymiana 25 miesięcy temu przy dopuszczalnym wieku 24 mies. — termin minął.
        recorder.setBatteryReplacementDate(LocalDate.now().minusMonths(25));

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorder);

        assertThat(valueOf(rows, "Termin ważności ogniwa")).contains("termin minął");
    }

    @Test
    @DisplayName("Powinien pokazać metadane ostatniego odczytu")
    void shouldShowLastReadoutMetadata() {
        when(seriesRepository.countByThermoRecorderId(anyLong())).thenReturn(7L);
        when(seriesRepository.findReadoutSummaries(anyLong(), any())).thenReturn(List.of(
                new RecorderReadoutSummary(LocalDateTime.of(2026, 7, 15, 9, 30),
                        "jkowalski", 87, 15, 1200, "Komora A")));

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(replaceableModel()));

        assertThat(valueOf(rows, "Ostatni odczyt")).isEqualTo("15.07.2026 09:30");
        assertThat(valueOf(rows, "Odczyt wykonał")).isEqualTo("jkowalski");
        assertThat(valueOf(rows, "Komora")).isEqualTo("Komora A");
        assertThat(valueOf(rows, "Poziom baterii przy odczycie")).isEqualTo("87 %");
        assertThat(valueOf(rows, "Liczba odczytów w historii")).isEqualTo("7");
    }

    @Test
    @DisplayName("Sentinel -1 z importu PDF nie powinien udawać poziomu baterii")
    void shouldNotShowSentinelBatteryLevel() {
        when(seriesRepository.findReadoutSummaries(anyLong(), any())).thenReturn(List.of(
                new RecorderReadoutSummary(LocalDateTime.of(2026, 7, 15, 9, 30),
                        "jkowalski", -1, 15, 1200, null)));

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(replaceableModel()));

        assertThat(valueOf(rows, "Poziom baterii przy odczycie")).isEqualTo("— brak danych");
        assertThat(valueOf(rows, "Komora")).isEqualTo("— brak danych");
    }

    @Test
    @DisplayName("Rejestrator bez modelu nie powinien wywracać karty")
    void shouldSurviveRecorderWithoutModel() {
        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(null));

        assertThat(rows).isNotEmpty();
        assertThat(valueOf(rows, "Model")).isEqualTo("— brak danych");
        assertThat(valueOf(rows, "Szacowany czas pracy")).isEqualTo("— brak danych");
    }

    @Test
    @DisplayName("Karta powinna tłumaczyć się po przełączeniu na angielski")
    void shouldTranslateCard() {
        I18n.init("en");
        ThermoRecorder recorder = recorderWith(replaceableModel());
        recorder.setLastBatteryLevelPercent(50);

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorder);

        assertThat(rows).extracting(RecorderDetailProperty::section).contains("Identification");
        assertThat(valueOf(rows, "Serial number")).isEqualTo("SN-001");
        assertThat(valueOf(rows, "Estimated runtime"))
                .isEqualTo("≈ 250.0 days at the 15 min reference cycle");
        assertThat(valueOf(rows, "Laboratory")).isEqualTo("— no data");
    }

    @Test
    @DisplayName("Najnowsze świadectwo powinno trafić na kartę wraz z zakresem wzorcowania")
    void shouldShowLatestCalibration() {
        Calibration latest = Calibration.builder()
                .calibrationDate(LocalDate.of(2026, 3, 10))
                .certificateNumber("PCA/2026/123")
                .validUntil(LocalDate.now().plusDays(40))
                .channelNumber(1)
                .build();
        when(calibrationRepository.findByThermoRecorderIdOrderByCalibrationDateDesc(anyLong()))
                .thenReturn(List.of(latest));

        List<RecorderDetailProperty> rows = detailsService.buildDetails(recorderWith(replaceableModel()));

        assertThat(valueOf(rows, "Data ostatniego wzorcowania")).isEqualTo("10.03.2026");
        assertThat(valueOf(rows, "Numer świadectwa")).isEqualTo("PCA/2026/123");
        assertThat(valueOf(rows, "Ważne do")).contains("pozostało 40 dni");
        assertThat(valueOf(rows, "Liczba świadectw w kartotece")).isEqualTo("1");
    }
}