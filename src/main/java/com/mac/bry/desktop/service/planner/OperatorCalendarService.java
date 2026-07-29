package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.OperatorShiftConfig;
import com.mac.bry.desktop.model.UserVacation;
import com.mac.bry.desktop.repository.OperatorShiftConfigRepository;
import com.mac.bry.desktop.repository.UserVacationRepository;
import com.mac.bry.desktop.security.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Wyznacza okna, w których wolno planować akcje manualne technika —
 * Krok 1 (programowanie), Krok 2 (umieszczenie) i Krok 5 (odczyt) — z
 * uwzględnieniem godzin zmiany, weekendów, świąt i nieobecności (reguła W9).
 * <p>
 * Kroki 3 i 4 są autonomiczne: rejestrator pracuje w komorze także nocą,
 * w weekendy i święta, więc ta klasa ich nie ogranicza.
 * <p>
 * Wszystkie obliczenia prowadzone są w strefie {@link #ZONE}. Pomiar z Kroku 4
 * trwa ok. 120 h i regularnie przechodzi przez zmianę czasu, więc godzina okna
 * roboczego musi być stała w czasie lokalnym, a nie w UTC (ST-CAL-03).
 */
@Service
public class OperatorCalendarService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    /**
     * Zabezpieczenie przed pętlą nieskończoną, gdyby konfiguracja nie miała
     * ani jednego dnia roboczego.
     */
    private static final int MAX_DAYS_LOOKAHEAD = 400;

    private final OperatorShiftConfigRepository shiftConfigRepository;
    private final UserVacationRepository vacationRepository;
    private final PolishHolidayProvider holidayProvider;

    public OperatorCalendarService(OperatorShiftConfigRepository shiftConfigRepository,
                                   UserVacationRepository vacationRepository,
                                   PolishHolidayProvider holidayProvider) {
        this.shiftConfigRepository = shiftConfigRepository;
        this.vacationRepository = vacationRepository;
        this.holidayProvider = holidayProvider;
    }

    /**
     * Konfiguracja zmiany operatora — własna, a w razie jej braku globalna.
     *
     * @throws IllegalStateException gdy nie ma nawet konfiguracji globalnej
     */
    public OperatorShiftConfig resolveShiftConfig(User user) {
        if (user != null) {
            var personal = shiftConfigRepository.findByUserAndActiveTrue(user);
            if (personal.isPresent()) {
                return personal.get();
            }
        }
        return shiftConfigRepository.findFirstByUserIsNullAndActiveTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Brak aktywnej globalnej konfiguracji okna pracy operatora (operator_shift_configs)"));
    }

    /**
     * Czy w danym dniu wolno zaplanować akcję manualną: dzień pracujący wg
     * konfiguracji, nie święto i nie nieobecność.
     */
    public boolean isWorkingDay(LocalDate date, User user) {
        OperatorShiftConfig config = resolveShiftConfig(user);
        List<UserVacation> vacations = vacationRepository.findOverlapping(user, date, date);
        return isWorkingDay(date, config, vacations);
    }

    /**
     * Czy moment mieści się w oknie roboczym — dzień pracujący i godzina
     * w granicach zmiany.
     */
    public boolean isWithinWorkingWindow(LocalDateTime moment, User user) {
        OperatorShiftConfig config = resolveShiftConfig(user);
        List<UserVacation> vacations =
                vacationRepository.findOverlapping(user, moment.toLocalDate(), moment.toLocalDate());
        return isWorkingDay(moment.toLocalDate(), config, vacations)
                && config.isWithinShift(moment.toLocalTime());
    }

    /**
     * Najbliższy moment od {@code desired}, w którym wolno wykonać akcję manualną.
     * <p>
     * Gdy {@code desired} już mieści się w oknie roboczym, zwracany jest bez
     * zmiany. Gdy wypada przed zmianą tego samego dnia roboczego — początek tej
     * zmiany. W pozostałych przypadkach — początek zmiany w najbliższym dniu
     * roboczym (ST-W9-01, ST-CAL-01).
     */
    public LocalDateTime findNextValidShiftStart(LocalDateTime desired, User user) {
        OperatorShiftConfig config = resolveShiftConfig(user);

        LocalDate from = desired.toLocalDate();
        // Jedno zapytanie na cały horyzont zamiast osobnego dla każdego kandydata.
        List<UserVacation> vacations =
                vacationRepository.findOverlapping(user, from, from.plusDays(MAX_DAYS_LOOKAHEAD));

        for (int offset = 0; offset <= MAX_DAYS_LOOKAHEAD; offset++) {
            LocalDate candidate = from.plusDays(offset);
            if (!isWorkingDay(candidate, config, vacations)) {
                continue;
            }
            if (offset > 0) {
                return candidate.atTime(config.getShiftStart());
            }
            // Dzień samego zlecenia — decyduje godzina.
            if (desired.toLocalTime().isAfter(config.getShiftEnd())) {
                continue; // zmiana już się skończyła, szukamy dalej
            }
            return desired.toLocalTime().isBefore(config.getShiftStart())
                    ? candidate.atTime(config.getShiftStart())
                    : desired;
        }

        throw new IllegalStateException(
                "Nie znaleziono dnia roboczego w ciągu " + MAX_DAYS_LOOKAHEAD
                        + " dni od " + desired + " — sprawdź konfigurację okna pracy operatora");
    }

    public LocalDateTime findNextValidShiftStart(LocalDateTime desired) {
        return findNextValidShiftStart(desired, null);
    }

    /**
     * Początek zmiany pierwszego dnia roboczego po powrocie z nieobecności —
     * używane przy rekalkulacji po nieplanowanym L4 (ST-L4-01).
     */
    public LocalDateTime findFirstShiftStartAfter(UserVacation absence, User user) {
        OperatorShiftConfig config = resolveShiftConfig(user);
        return findNextValidShiftStart(absence.firstDayBack().atTime(config.getShiftStart()), user);
    }

    private boolean isWorkingDay(LocalDate date, OperatorShiftConfig config, List<UserVacation> vacations) {
        if (!config.worksOn(date.getDayOfWeek())) {
            return false;
        }
        if (holidayProvider.isHoliday(date)) {
            return false;
        }
        return vacations.stream().noneMatch(v -> v.covers(date));
    }
}