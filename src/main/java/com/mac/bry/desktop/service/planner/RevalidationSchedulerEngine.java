package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.repository.ProcedureClassConfigRepository;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.planner.exception.RecorderAllocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;

/**
 * Silnik planowania rewalidacji okresowych i mapowań.
 * <p>
 * Jednostką planowania jest komora, nie urządzenie (BA R1) — zapotrzebowanie
 * urządzenia to suma zapotrzebowań jego komór.
 */
@Service
public class RevalidationSchedulerEngine {

    private static final Logger log = LoggerFactory.getLogger(RevalidationSchedulerEngine.class);

    /** Liczba rejestratorów dla pełnego mapowania GxP, niezależnie od kubatury (BA R1). */
    private static final int MAPPING_RECORDER_COUNT = 8;

    /** Krok przeszukiwania okna startu — kwadrans odpowiada praktyce laboratoryjnej. */
    private static final int WINDOW_SEARCH_GRANULARITY_MINUTES = 15;

    /** Horyzont poszukiwania okna, w dniach roboczych operatora. */
    private static final int WINDOW_SEARCH_DAYS = 90;

    private final CoolingChamberRepository chamberRepository;
    private final ProcedureClassConfigRepository procedureClassConfigRepository;
    private final PlannedValidationTaskRepository taskRepository;
    private final ValidationPlanNumberRepository validationPlanNumberRepository;
    private final OperatorCalendarService calendarService;
    private final TestoDelayCalculatorService delayCalculator;
    private final RecorderAllocationService allocationService;

    public RevalidationSchedulerEngine(CoolingChamberRepository chamberRepository,
                                       ProcedureClassConfigRepository procedureClassConfigRepository,
                                       PlannedValidationTaskRepository taskRepository,
                                       ValidationPlanNumberRepository validationPlanNumberRepository,
                                       OperatorCalendarService calendarService,
                                       TestoDelayCalculatorService delayCalculator,
                                       RecorderAllocationService allocationService) {
        this.chamberRepository = chamberRepository;
        this.procedureClassConfigRepository = procedureClassConfigRepository;
        this.taskRepository = taskRepository;
        this.validationPlanNumberRepository = validationPlanNumberRepository;
        this.calendarService = calendarService;
        this.delayCalculator = delayCalculator;
        this.allocationService = allocationService;
    }

    // ------------------------------------------------------------------
    // R1 / R2 — zapotrzebowanie i kwalifikacja procedury
    // ------------------------------------------------------------------

    /**
     * Liczba fizycznych rejestratorów wymaganych dla komory (macierz BA R1).
     * <p>
     * Dla mapowania obowiązek wynika z materiału: odczynniki i próby
     * środowiskowe są z niego zwolnione i zwracają 0 (ST-R2-02).
     * <p>
     * Wynik liczy <b>rejestratory</b>, nie punkty pomiarowe — minimalną liczbę
     * punktów pilnuje niezależnie {@link RecorderAllocationService}.
     */
    public int calculateRequiredLoggers(CoolingChamber chamber, GxPProcedureType procedureType) {
        if (procedureType == GxPProcedureType.MAPPING) {
            return chamber.isMappingRequired() ? MAPPING_RECORDER_COUNT : 0;
        }
        if (chamber.getVolumeCategory() == null) {
            throw new IllegalStateException(
                    "Komora " + chamber.getChamberName() + " nie ma ustalonej klasy kubatury (VolumeCategory)");
        }
        return switch (chamber.getVolumeCategory()) {
            case SMALL -> 2;
            case MEDIUM -> 4;
            case LARGE -> 8;
        };
    }

    /**
     * Sumaryczne zapotrzebowanie urządzenia — suma po jego komorach
     * (chłodziarko-zamrażarka = 2 + 2 = 4 rejestratory, ST-R1-02).
     */
    public int calculateRequiredLoggersForDevice(CoolingDevice device, GxPProcedureType procedureType) {
        return device.getChambers().stream()
                .mapToInt(chamber -> calculateRequiredLoggers(chamber, procedureType))
                .sum();
    }

    /**
     * Procedury, których termin dla komory wypada nie później niż na koniec
     * podanego roku.
     * <p>
     * Materiał krytyczny wymaga <b>obu</b> procedur — corocznej rewalidacji
     * i mapowania co 5 lat (BA R2) — więc w roku mapowania komora może dostać
     * dwa zadania. Każda procedura liczona z własnego zegara; mieszanie tych
     * dat zresetowałoby 5-letni cykl.
     */
    public List<ProcedureDue> determineDueProcedures(CoolingChamber chamber, int year, LocalDate today) {
        LocalDate endOfYear = LocalDate.of(year, 12, 31);
        List<ProcedureDue> due = new ArrayList<>();

        LocalDate revalidationDue = chamber.getNextDueDate(GxPProcedureType.PERIODIC_REVALIDATION, today);
        if (!revalidationDue.isAfter(endOfYear)) {
            due.add(new ProcedureDue(GxPProcedureType.PERIODIC_REVALIDATION, revalidationDue));
        }

        if (chamber.isMappingRequired()) {
            LocalDate mappingDue = chamber.getNextDueDate(GxPProcedureType.MAPPING, today);
            if (!mappingDue.isAfter(endOfYear)) {
                due.add(new ProcedureDue(GxPProcedureType.MAPPING, mappingDue));
            }
        }
        return due;
    }

    // ------------------------------------------------------------------
    // Generowanie planu
    // ------------------------------------------------------------------

    /**
     * Generuje plan roczny dla wszystkich komór urządzeń aktywnych.
     * <p>
     * Zadania układane są wg terminu rosnąco, więc komory przeterminowane
     * sięgają po ograniczoną pulę rejestratorów jako pierwsze (W6).
     * Zadanie, którego nie da się obsadzić, zostaje zapisane ze statusem
     * {@link PlannedTaskStatus#PLANNED} i opisaną przyczyną — plan pokazuje
     * lukę, zamiast ją przemilczeć.
     */
    @Transactional
    public List<PlannedValidationTask> generateYearlySchedule(int year) {
        LocalDate today = LocalDate.now();
        List<CoolingChamber> chambers =
                chamberRepository.findByCoolingDeviceStatusOrderByIdAsc(DeviceStatus.ACTIVE);

        record Candidate(CoolingChamber chamber, ProcedureDue due) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (CoolingChamber chamber : chambers) {
            for (ProcedureDue due : determineDueProcedures(chamber, year, today)) {
                if (calculateRequiredLoggers(chamber, due.procedureType()) == 0) {
                    continue; // zwolnienie z mapowania dla odczynników (BA R2)
                }
                candidates.add(new Candidate(chamber, due));
            }
        }

        candidates.sort(Comparator.comparing((Candidate c) -> c.due().dueDate())
                .thenComparing(c -> c.chamber().getId()));

        List<PlannedValidationTask> scheduled = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (alreadyPlanned(candidate.chamber(), candidate.due())) {
                continue;
            }
            scheduled.add(planTask(candidate.chamber(), candidate.due().procedureType(),
                    candidate.due().dueDate(), year));
        }
        return scheduled;
    }

    /**
     * Planuje pojedyncze zadanie: dobiera okno startu, wylicza łańcuch kroków
     * i próbuje obsadzić rejestratory.
     */
    @Transactional
    public PlannedValidationTask planTask(CoolingChamber chamber, GxPProcedureType procedureType,
                                          LocalDate dueDate, int year) {
        ProcedureClassConfig config = resolveProcedureClass(procedureType);
        LocalDate today = LocalDate.now();

        // Badanie ma się odbyć przed upływem terminu (W6); dla komór już
        // przeterminowanych startujemy najwcześniej jak się da.
        LocalDateTime earliestStart = (dueDate.isBefore(today) ? today : dueDate.minusDays(14))
                .atTime(LocalTime.MIDNIGHT);
        if (earliestStart.toLocalDate().isBefore(today)) {
            earliestStart = today.atTime(LocalTime.MIDNIGHT);
        }

        StepChain chain = fitTaskToShift(earliestStart, config)
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "Nie znaleziono okna roboczego dla komory %s w ciągu %d dni od %s",
                        chamber.getChamberName(), WINDOW_SEARCH_DAYS, dueDate)));

        PlannedValidationTask task = PlannedValidationTask.builder()
                .taskNumber(resolveTaskNumber(chamber, year))
                .coolingChamber(chamber)
                .procedureClassConfig(config)
                .procedureType(procedureType)
                .dueDate(dueDate)
                .plannedStep1Time(chain.step1())
                .plannedStep2Time(chain.step2())
                .plannedStep3StabEnd(chain.stabilizationEnd())
                .plannedStep4MapEnd(chain.measurementEnd())
                .plannedStep5ReadoutDeadline(chain.readoutDeadline())
                .calculatedTestoDelayMinutes(delayCalculator.calculateStartDelay(config))
                .requiredRecorderCount(calculateRequiredLoggers(chamber, procedureType))
                .status(PlannedTaskStatus.PLANNED)
                .resourceStatus(TaskResourceStatus.OK)
                .build();

        validateZeroJunkData(task, config);
        task = taskRepository.save(task);

        try {
            allocationService.allocateRecorders(task, chamber);
        } catch (RecorderAllocationException e) {
            markResourceShortage(task, e);
        }
        return taskRepository.save(task);
    }

    /**
     * W3 (Zero-Junk Data) — rejestracja nie może ruszyć przed końcem
     * stabilizacji, inaczej do raportu GxP trafiłyby próbki z fazy rozruchu.
     */
    public void validateZeroJunkData(PlannedValidationTask task, ProcedureClassConfig config) {
        LocalDateTime expectedFirstSample =
                delayCalculator.calculateFirstSampleTime(config, task.getPlannedStep2Time());

        if (!task.getPlannedStep3StabEnd().equals(expectedFirstSample)) {
            throw new IllegalStateException(String.format(
                    "Naruszenie W3 (Zero-Junk Data): pierwsza próbka zaplanowana na %s, "
                            + "a stabilizacja kończy się %s",
                    expectedFirstSample, task.getPlannedStep3StabEnd()));
        }
        if (task.getPlannedStep3StabEnd().isBefore(task.getPlannedStep2Time())) {
            throw new IllegalStateException(
                    "Naruszenie W3: koniec stabilizacji przed umieszczeniem rejestratorów w komorze");
        }
    }

    // ------------------------------------------------------------------
    // Dopasowanie okna
    // ------------------------------------------------------------------

    /**
     * Szuka najwcześniejszego startu, przy którym <b>wszystkie</b> akcje
     * manualne wypadają w oknie roboczym: programowanie i umieszczenie
     * (Kroki 1–2) oraz odczyt (Krok 5).
     * <p>
     * Odległość od startu do terminu odczytu jest stała (ok. 126 h), więc
     * przesunięcie startu o Δ przesuwa o Δ także odczyt. Przeszukiwanie idzie
     * po dniach roboczych, a wewnątrz dnia co kwadrans — zamknięta formuła
     * byłaby krucha przy zmianie czasu, bo długość doby nie jest stała.
     */
    public Optional<StepChain> fitTaskToShift(LocalDateTime earliestStart, ProcedureClassConfig config) {
        OperatorShiftConfig shift = calendarService.resolveShiftConfig(null);
        LocalDate searchFrom = earliestStart.toLocalDate();
        // Odczyt wypada ok. 6 dni po starcie, więc horyzont dni roboczych musi być szerszy.
        LocalDate searchTo = searchFrom.plusDays(WINDOW_SEARCH_DAYS + 30L);
        NavigableSet<LocalDate> workingDays = calendarService.workingDaysBetween(searchFrom, searchTo, null);

        long shiftMinutes = ChronoUnit.MINUTES.between(shift.getShiftStart(), shift.getShiftEnd());
        long latestStartOffset = shiftMinutes - config.getStep1ProgMinutes();

        int daysChecked = 0;
        for (LocalDate day : workingDays) {
            if (daysChecked++ >= WINDOW_SEARCH_DAYS) {
                break;
            }
            for (long offset = 0; offset <= latestStartOffset; offset += WINDOW_SEARCH_GRANULARITY_MINUTES) {
                LocalDateTime step1 = day.atTime(shift.getShiftStart()).plusMinutes(offset);
                if (step1.isBefore(earliestStart)) {
                    continue;
                }
                LocalDateTime step2 = step1.plusMinutes(config.getStep1ProgMinutes());
                LocalDateTime measurementEnd = delayCalculator.calculateMeasurementEnd(config, step2);
                LocalDateTime readoutDeadline = delayCalculator.calculateReadoutDeadline(config, step2);

                Optional<LocalDateTime> appointment =
                        findReadoutAppointment(measurementEnd, readoutDeadline, shift, workingDays);

                if (appointment.isPresent()) {
                    return Optional.of(new StepChain(
                            step1,
                            step2,
                            delayCalculator.calculateFirstSampleTime(config, step2),
                            measurementEnd,
                            appointment.get(),
                            readoutDeadline));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Najwcześniejszy moment, w którym technik może wykonać odczyt.
     * <p>
     * Krok 5 nie jest punktem w czasie, tylko przedziałem od zatrzymania zapisu
     * do nieprzekraczalnego terminu — dokument nazywa go „dopuszczalnym czasem/
     * buforem”. Wystarczy więc, by ten przedział przeciął okno zmiany w dniu
     * roboczym; wymaganie, żeby sam termin wypadał w oknie, byłoby przy
     * wartościach z BA §3 niespełnialne (od startu do terminu upływa 132,5 h,
     * czyli 12,5 h ponad pełne doby, co wypycha odczyt na wieczór lub noc
     * niezależnie od godziny startu).
     *
     * @return moment odczytu albo pusty, gdy przedział nie trafia w żadną zmianę
     */
    private Optional<LocalDateTime> findReadoutAppointment(LocalDateTime measurementEnd,
                                                           LocalDateTime readoutDeadline,
                                                           OperatorShiftConfig shift,
                                                           NavigableSet<LocalDate> workingDays) {
        for (LocalDate date = measurementEnd.toLocalDate();
             !date.isAfter(readoutDeadline.toLocalDate());
             date = date.plusDays(1)) {

            if (!workingDays.contains(date)) {
                continue;
            }
            LocalDateTime shiftOpens = date.atTime(shift.getShiftStart());
            LocalDateTime shiftCloses = date.atTime(shift.getShiftEnd());

            LocalDateTime earliest = measurementEnd.isAfter(shiftOpens) ? measurementEnd : shiftOpens;
            LocalDateTime latest = readoutDeadline.isBefore(shiftCloses) ? readoutDeadline : shiftCloses;

            if (!earliest.isAfter(latest)) {
                return Optional.of(earliest);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Pomocnicze
    // ------------------------------------------------------------------

    private void markResourceShortage(PlannedValidationTask task, RecorderAllocationException e) {
        task.setResourceStatus(e.getResourceStatus());
        task.setShortageReason(e.getMessage());
        task.setSuggestedWindowStart(e.getSuggestedWindowStart());
        log.warn("Zadanie {} dla komory {} bez obsady rejestratorów: {}",
                task.getTaskNumber(), task.getCoolingChamber().getChamberName(), e.getMessage());
    }

    private boolean alreadyPlanned(CoolingChamber chamber, ProcedureDue due) {
        return taskRepository.findByCoolingChamberAndProcedureTypeAndDueDate(
                chamber, due.procedureType(), due.dueDate()).isPresent();
    }

    private ProcedureClassConfig resolveProcedureClass(GxPProcedureType procedureType) {
        return procedureClassConfigRepository
                .findFirstByProcedureTypeAndActiveTrueOrderByNameAsc(procedureType)
                .orElseThrow(() -> new IllegalStateException(
                        "Brak aktywnej klasy procedury dla typu " + procedureType));
    }

    /**
     * Numer RPW urządzenia w formacie {@code planNumber/skrótPracowni/rok}.
     * <p>
     * Numer jest wspólny dla wszystkich komór urządzenia — zadania go dzielą,
     * dlatego {@code task_number} nie ma ograniczenia UNIQUE. Gdy urządzenie
     * nie ma jeszcze numeru na dany rok, planer nadaje kolejny wolny: nadanie
     * numeracji Rocznego Planu Walidacji jest częścią generowania tego planu.
     */
    private String resolveTaskNumber(CoolingChamber chamber, int year) {
        CoolingDevice device = chamber.getCoolingDevice();
        return validationPlanNumberRepository.findByCoolingDeviceAndYear(device, year)
                .orElseGet(() -> {
                    Integer maxPlanNumber = validationPlanNumberRepository.findMaxPlanNumberByYear(year);
                    ValidationPlanNumber created = ValidationPlanNumber.builder()
                            .coolingDevice(device)
                            .year(year)
                            .planNumber(maxPlanNumber != null ? maxPlanNumber + 1 : 1)
                            .build();
                    return validationPlanNumberRepository.save(created);
                })
                .getFormattedRpw();
    }

    /** Procedura wraz z terminem wynikającym z jej własnego zegara. */
    public record ProcedureDue(GxPProcedureType procedureType, LocalDate dueDate) {
    }

    /**
     * Pełny łańcuch czasowy zadania.
     * <p>
     * {@code readoutAppointment} to moment, na który technik ma się stawić,
     * a {@code readoutDeadline} — nieprzekraczalny termin, po którym planer
     * podnosi alert W7. Utrwalany jest wyłącznie termin; moment stawienia się
     * wynika z niego i z okna zmiany, więc nie potrzebuje własnej kolumny.
     */
    public record StepChain(LocalDateTime step1,
                            LocalDateTime step2,
                            LocalDateTime stabilizationEnd,
                            LocalDateTime measurementEnd,
                            LocalDateTime readoutAppointment,
                            LocalDateTime readoutDeadline) {
    }
}