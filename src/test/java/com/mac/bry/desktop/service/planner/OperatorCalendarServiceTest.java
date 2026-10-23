package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.OperatorShiftConfig;
import com.mac.bry.desktop.model.UserVacation;
import com.mac.bry.desktop.repository.OperatorShiftConfigRepository;
import com.mac.bry.desktop.repository.UserVacationRepository;
import com.mac.bry.desktop.security.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ST-W9-01, ST-W9-02, ST-CAL-01 — okno pracy operatora 06:30–13:30.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperatorCalendarServiceTest {

    @Mock
    private OperatorShiftConfigRepository shiftConfigRepository;

    @Mock
    private UserVacationRepository vacationRepository;

    private OperatorCalendarService service;

    @BeforeEach
    void setUp() {
        service = new OperatorCalendarService(
                shiftConfigRepository, vacationRepository, new PolishHolidayProvider());

        when(shiftConfigRepository.findFirstByUserIsNullAndActiveTrue())
                .thenReturn(Optional.of(defaultShift()));
        when(vacationRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of());
    }

    private OperatorShiftConfig defaultShift() {
        return OperatorShiftConfig.builder()
                .shiftStart(LocalTime.of(6, 30))
                .shiftEnd(LocalTime.of(13, 30))
                .worksMonday(true).worksTuesday(true).worksWednesday(true)
                .worksThursday(true).worksFriday(true)
                .worksSaturday(false).worksSunday(false)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("ST-W9-01: piątek 16:00 → poniedziałek 06:30")
    void st_w9_01_afterShiftOnFridayMovesToMonday() {
        // 3.07.2026 to piątek
        LocalDateTime friday16 = LocalDateTime.of(2026, 7, 3, 16, 0);

        assertThat(service.findNextValidShiftStart(friday16))
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 6, 30));
    }

    @Test
    @DisplayName("Moment w oknie roboczym zostaje bez zmiany")
    void momentInsideWindowIsKept() {
        LocalDateTime mondayMorning = LocalDateTime.of(2026, 7, 6, 9, 15);

        assertThat(service.findNextValidShiftStart(mondayMorning)).isEqualTo(mondayMorning);
        assertThat(service.isWithinWorkingWindow(mondayMorning, null)).isTrue();
    }

    @Test
    @DisplayName("Przed zmianą tego samego dnia → początek tej zmiany")
    void beforeShiftMovesToShiftStart() {
        LocalDateTime mondayDawn = LocalDateTime.of(2026, 7, 6, 4, 0);

        assertThat(service.findNextValidShiftStart(mondayDawn))
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 6, 30));
    }

    @Test
    @DisplayName("Granice zmiany są domknięte — 13:30 jeszcze się liczy")
    void shiftBoundariesAreInclusive() {
        LocalDateTime mondayShiftEnd = LocalDateTime.of(2026, 7, 6, 13, 30);

        assertThat(service.findNextValidShiftStart(mondayShiftEnd)).isEqualTo(mondayShiftEnd);
        assertThat(service.findNextValidShiftStart(mondayShiftEnd.plusMinutes(1)))
                .isEqualTo(LocalDateTime.of(2026, 7, 7, 6, 30));
    }

    @Test
    @DisplayName("ST-W9-02: urlop 01–07.08.2026 wyklucza akcje manualne")
    void st_w9_02_vacationExcludesManualActions() {
        UserVacation vacation = UserVacation.builder()
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 7))
                .reason("Urlop wypoczynkowy")
                .unplannedL4(false)
                .build();
        when(vacationRepository.findOverlapping(any(), any(), any())).thenReturn(List.of(vacation));

        // 3.08.2026 to poniedziałek w środku urlopu
        LocalDateTime duringVacation = LocalDateTime.of(2026, 8, 3, 7, 0);

        // Powrót 8.08 to sobota → pierwszy dzień roboczy to poniedziałek 10.08
        assertThat(service.findNextValidShiftStart(duringVacation))
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 6, 30));
    }

    @Test
    @DisplayName("ST-CAL-01: urlop + święto + weekend przesuwają na pierwszy dzień roboczy po kumulacji")
    void st_cal_01_overlappingVacationHolidayAndWeekend() {
        // Boże Ciało 2026 = czwartek 4.06. Urlop 1–3.06 (pon–śr).
        // Piątek 5.06 wolny urlopowo → weekend 6–7.06 → poniedziałek 8.06.
        UserVacation vacation = UserVacation.builder()
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 5))
                .reason("Urlop wypoczynkowy")
                .build();
        when(vacationRepository.findOverlapping(any(), any(), any())).thenReturn(List.of(vacation));

        LocalDateTime target = LocalDateTime.of(2026, 6, 1, 7, 0);

        assertThat(service.findNextValidShiftStart(target))
                .isEqualTo(LocalDateTime.of(2026, 6, 8, 6, 30));
    }

    @Test
    @DisplayName("Święto ruchome blokuje planowanie — Poniedziałek Wielkanocny 2026")
    void easterMondayIsNotWorkingDay() {
        LocalDateTime easterMonday = LocalDateTime.of(2026, 4, 6, 7, 0);

        assertThat(service.isWorkingDay(easterMonday.toLocalDate(), null)).isFalse();
        assertThat(service.findNextValidShiftStart(easterMonday))
                .isEqualTo(LocalDateTime.of(2026, 4, 7, 6, 30));
    }

    @Test
    @DisplayName("ST-L4-01: powrót z L4 → początek zmiany pierwszego dnia roboczego")
    void firstShiftAfterAbsence() {
        // L4 kończy się w piątek 10.07.2026 → powrót 11.07 to sobota → poniedziałek 13.07
        UserVacation l4 = UserVacation.builder()
                .startDate(LocalDate.of(2026, 7, 8))
                .endDate(LocalDate.of(2026, 7, 10))
                .unplannedL4(true)
                .build();

        assertThat(service.findFirstShiftStartAfter(l4, null))
                .isEqualTo(LocalDateTime.of(2026, 7, 13, 6, 30));
    }

    @Test
    @DisplayName("Konfiguracja własna operatora ma pierwszeństwo przed globalną")
    void personalShiftConfigWinsOverGlobal() {
        User operator = new User();
        OperatorShiftConfig personal = defaultShift();
        personal.setShiftStart(LocalTime.of(8, 0));
        personal.setUser(operator);
        when(shiftConfigRepository.findByUserAndActiveTrue(eq(operator))).thenReturn(Optional.of(personal));

        assertThat(service.findNextValidShiftStart(LocalDateTime.of(2026, 7, 6, 4, 0), operator))
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 8, 0));
    }

    @Test
    @DisplayName("Brak globalnej konfiguracji to błąd konfiguracji, nie cicha akceptacja")
    void missingGlobalConfigFailsLoudly() {
        when(shiftConfigRepository.findFirstByUserIsNullAndActiveTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findNextValidShiftStart(LocalDateTime.of(2026, 7, 6, 7, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("globalnej konfiguracji okna pracy");
    }

    @Test
    @DisplayName("Konfiguracja bez dnia roboczego nie zapętla planera")
    void noWorkingDayThrowsInsteadOfLooping() {
        OperatorShiftConfig noWorkDays = defaultShift();
        noWorkDays.setWorksMonday(false);
        noWorkDays.setWorksTuesday(false);
        noWorkDays.setWorksWednesday(false);
        noWorkDays.setWorksThursday(false);
        noWorkDays.setWorksFriday(false);
        when(shiftConfigRepository.findFirstByUserIsNullAndActiveTrue()).thenReturn(Optional.of(noWorkDays));

        assertThatThrownBy(() -> service.findNextValidShiftStart(LocalDateTime.of(2026, 7, 6, 7, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nie znaleziono dnia roboczego");
    }
}