package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.repository.CalibrationRepository;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testy dziennego digestu powiadomień GxP (wzorcowania + rewalidacje).
 */
@ExtendWith(MockitoExtension.class)
class GxpNotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 5);

    @InjectMocks
    private GxpNotificationService service;

    @Mock private CalibrationRepository calibrationRepository;
    @Mock private CoolingChamberRepository coolingChamberRepository;
    @Mock private UserService userService;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;

    @BeforeEach
    void configureDefaults() {
        ReflectionTestUtils.setField(service, "advanceDays", 30);
        ReflectionTestUtils.setField(service, "revalidationCycleMonths", 12);
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    @DisplayName("TC-NOTIF-001: Brak wygasających pozycji → digest pusty, e-mail niewysłany")
    void tc_notif_001_nothingExpiring_noEmail() {
        when(calibrationRepository.findLatestExpiringUntil(any())).thenReturn(List.of());
        when(coolingChamberRepository.findAll()).thenReturn(List.of());

        assertThat(service.buildDigest(TODAY)).isEmpty();

        service.sendDailyDigest();
        verify(emailService, never()).sendMassEmail(anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("TC-NOTIF-002: Wygasające świadectwo → sekcja z S/N, numerem i liczbą dni")
    void tc_notif_002_expiringCalibrationListed() {
        when(calibrationRepository.findLatestExpiringUntil(TODAY.plusDays(30)))
                .thenReturn(List.of(calibration("44373263", "SW/2026/041", TODAY.plusDays(10))));
        when(coolingChamberRepository.findAll()).thenReturn(List.of());

        String digest = service.buildDigest(TODAY).orElseThrow();

        assertThat(digest)
                .contains("ŚWIADECTWA WZORCOWANIA (1)")
                .contains("44373263")
                .contains("SW/2026/041")
                .contains("wygasa za 10 dni");
    }

    @Test
    @DisplayName("TC-NOTIF-003: Przeterminowane świadectwo oznaczone jako PRZETERMINOWANE")
    void tc_notif_003_expiredCalibrationFlagged() {
        when(calibrationRepository.findLatestExpiringUntil(any()))
                .thenReturn(List.of(calibration("11111111", "SW/2025/007", TODAY.minusDays(5))));
        when(coolingChamberRepository.findAll()).thenReturn(List.of());

        String digest = service.buildDigest(TODAY).orElseThrow();

        assertThat(digest).contains("PRZETERMINOWANE od 5 dni");
    }

    @Test
    @DisplayName("TC-NOTIF-004: Komora po terminie rocznej rewalidacji trafia do digestu")
    void tc_notif_004_overdueChamberListed() {
        when(calibrationRepository.findLatestExpiringUntil(any())).thenReturn(List.of());
        when(coolingChamberRepository.findAll()).thenReturn(List.of(
                chamber("Komora Górna", TODAY.minusMonths(13), true)));

        String digest = service.buildDigest(TODAY).orElseThrow();

        assertThat(digest)
                .contains("KOMORY DO REWALIDACJI (1)")
                .contains("Komora Górna")
                .contains("ZALEGŁA");
    }

    @Test
    @DisplayName("TC-NOTIF-005: Komora bez mapowania wymaga kwalifikacji")
    void tc_notif_005_neverMappedChamberListed() {
        when(calibrationRepository.findLatestExpiringUntil(any())).thenReturn(List.of());
        when(coolingChamberRepository.findAll()).thenReturn(List.of(
                chamber("Komora Nowa", null, true)));

        String digest = service.buildDigest(TODAY).orElseThrow();

        assertThat(digest).contains("BRAK MAPOWANIA");
    }

    @Test
    @DisplayName("TC-NOTIF-006: Komora bez wymogu mapowania i świeżo mapowana — pomijane")
    void tc_notif_006_notDueChambersSkipped() {
        when(calibrationRepository.findLatestExpiringUntil(any())).thenReturn(List.of());
        when(coolingChamberRepository.findAll()).thenReturn(List.of(
                chamber("Bez wymogu", null, false),
                chamber("Świeża", TODAY.minusMonths(2), true)));

        assertThat(service.buildDigest(TODAY)).isEmpty();
    }

    @Test
    @DisplayName("TC-NOTIF-007: Digest wysyłany do super adminów + wpis w audit logu")
    void tc_notif_007_digestSentAndAudited() {
        when(calibrationRepository.findLatestExpiringUntil(any()))
                .thenReturn(List.of(calibration("22222222", "SW/2026/099", TODAY.plusDays(3))));
        when(coolingChamberRepository.findAll()).thenReturn(List.of());
        when(userService.getSuperAdminEmails()).thenReturn(List.of("admin@lab.pl"));

        service.sendDailyDigest();

        verify(emailService).sendMassEmail(
                org.mockito.ArgumentMatchers.eq(List.of("admin@lab.pl")),
                org.mockito.ArgumentMatchers.contains("dzienny przegląd GxP"),
                org.mockito.ArgumentMatchers.contains("22222222"));
        verify(auditService).logAccessEvent(
                org.mockito.ArgumentMatchers.eq("SYSTEM"),
                org.mockito.ArgumentMatchers.eq("GXP_NOTIFICATION"),
                anyString());
    }

    @Test
    @DisplayName("TC-NOTIF-008: Flaga enabled=false wyłącza wysyłkę całkowicie")
    void tc_notif_008_disabledFlagSkipsEverything() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.sendDailyDigest();

        verify(emailService, never()).sendMassEmail(anyList(), anyString(), anyString());
        verify(calibrationRepository, never()).findLatestExpiringUntil(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Calibration calibration(String serial, String certNo, LocalDate validUntil) {
        return Calibration.builder()
                .thermoRecorder(ThermoRecorder.builder().serialNumber(serial).build())
                .certificateNumber(certNo)
                .calibrationDate(validUntil.minusYears(1))
                .validUntil(validUntil)
                .build();
    }

    private CoolingChamber chamber(String name, LocalDate lastMapping, boolean mappingRequired) {
        return CoolingChamber.builder()
                .chamberName(name)
                .lastMappingDate(lastMapping)
                .materialType(MaterialType.builder().requiresMapping(mappingRequired).build())
                .coolingDevice(CoolingDevice.builder()
                        .name("Chłodziarka testowa")
                        .inventoryNumber("UC/2026/01")
                        .build())
                .build();
    }
}
