package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.repository.ThermoRecorderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Zapis stanu baterii do kartoteki — dana wejściowa reguły W4c planera.
 * <p>
 * Sedno tych testów: pole {@code batteryRemainingDays} rozstrzyga, czy planer w ogóle
 * dopuści rejestrator do badania. Zapisanie tam sentinela `-1` dałoby ujemny budżet
 * energii, a pominięcie zapisu — trwałą blokadę mimo poprawnego odczytu sprzętu.
 */
@ExtendWith(MockitoExtension.class)
class RecorderBatteryServiceTest {

    @Mock
    private ThermoRecorderRepository thermoRecorderRepository;

    @InjectMocks
    private RecorderBatteryService service;

    private ThermoRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = ThermoRecorder.builder()
                .id(1L)
                .serialNumber("SN-174-001")
                .build();
    }

    @Test
    @DisplayName("Odczyt z ramki ab010a trafia do kartoteki wraz z datą odczytu")
    void savesRemainingDaysAndReadTimestamp() {
        LocalDateTime beforeCall = LocalDateTime.now();

        boolean saved = service.recordReading(recorder, 388);

        assertThat(saved).isTrue();
        assertThat(recorder.getBatteryRemainingDays()).isEqualTo(388);
        assertThat(recorder.getLastBatteryReadAt())
                .as("bez daty odczytu nie da się ocenić, czy stan baterii jest jeszcze aktualny")
                .isNotNull()
                .isAfterOrEqualTo(beforeCall);
        verify(thermoRecorderRepository).save(recorder);
    }

    @Test
    @DisplayName("Sentinel -1 nie trafia do bazy — W4c policzyłaby z niego ujemny budżet")
    void rejectsSentinelValue() {
        boolean saved = service.recordReading(recorder, -1);

        assertThat(saved).isFalse();
        assertThat(recorder.getBatteryRemainingDays())
                .as("brak danych jest uczciwy, liczba ujemna nie")
                .isNull();
        assertThat(recorder.getLastBatteryReadAt())
                .as("data odczytu bez wartości byłaby śladem pomiaru, którego nie było")
                .isNull();
        verify(thermoRecorderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Zerowe wskazanie jest zapisywane — to wyczerpana bateria, nie brak danych")
    void savesZeroDays() {
        boolean saved = service.recordReading(recorder, 0);

        assertThat(saved).isTrue();
        assertThat(recorder.getBatteryRemainingDays()).isZero();
        verify(thermoRecorderRepository).save(recorder);
    }

    @Test
    @DisplayName("Wyszukanie po numerze seryjnym aktualizuje właściwy egzemplarz")
    void findsRecorderBySerialNumber() {
        when(thermoRecorderRepository.findBySerialNumber("SN-174-001")).thenReturn(Optional.of(recorder));

        boolean saved = service.recordReading("SN-174-001", 42);

        assertThat(saved).isTrue();
        assertThat(recorder.getBatteryRemainingDays()).isEqualTo(42);
        verify(thermoRecorderRepository).save(recorder);
    }

    /**
     * Zakładka „Odczyt Testo” służy też do podglądu sprzętu jeszcze niezarejestrowanego,
     * więc nieznany numer seryjny nie może wywracać odczytu. Rygor GxP (blokada na
     * nieznanym S/N) obowiązuje w procedurze rewalidacji, nie tutaj.
     */
    @Test
    @DisplayName("Nieznany numer seryjny nie jest błędem — zwraca false i nic nie zapisuje")
    void unknownSerialNumberIsNotAnError() {
        when(thermoRecorderRepository.findBySerialNumber("SN-OBCY")).thenReturn(Optional.empty());

        boolean saved = service.recordReading("SN-OBCY", 100);

        assertThat(saved).isFalse();
        verify(thermoRecorderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pusty numer seryjny nie odpytuje bazy")
    void blankSerialNumberShortCircuits() {
        assertThat(service.recordReading("   ", 100)).isFalse();
        assertThat(service.recordReading((String) null, 100)).isFalse();

        verify(thermoRecorderRepository, never()).findBySerialNumber(any());
        verify(thermoRecorderRepository, never()).save(any());
    }
}