package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.ProcedureClassConfig;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.service.planner.dto.HardwareBudget;
import com.mac.bry.desktop.service.planner.dto.HardwareViolation;
import com.mac.bry.desktop.service.planner.exception.HardwareDataIncompleteException;
import com.mac.bry.desktop.service.planner.exception.InsufficientBatteryLevelException;
import com.mac.bry.desktop.service.planner.exception.InsufficientRecorderMemoryException;
import com.mac.bry.desktop.service.planner.exception.RecorderAllocationException;
import com.mac.bry.desktop.service.planner.exception.RecorderOutOfOperatingRangeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Reguła W4 — limity sprzętowe rejestratora wobec konkretnego badania.
 * <p>
 * Trzy niezależne kryteria, sprawdzane w tej kolejności:
 * <ul>
 *   <li><b>W4a — zakres pracy</b>: temperatura komory mieści się w zakresie modelu.
 *       Bramka twarda: rejestrator poza zakresem odpada niezależnie od baterii.</li>
 *   <li><b>W4b — pamięć</b>: liczba próbek Kroku 4 mieści się w buforze
 *       <i>przypadającym na kanał</i>.</li>
 *   <li><b>W4c — energia</b>: budżet dni pracy pokrywa pełny czas misji
 *       z zapasem bezpieczeństwa.</li>
 * </ul>
 *
 * <h2>Dlaczego budżet w dniach, a nie procent baterii</h2>
 * Testo specyfikuje żywotność jako liczbę dni przy referencyjnym cyklu i
 * temperaturze (174 T: 500 dni @ 15 min / +25 °C; 184 T4: 100 dni @ 15 min /
 * -80 °C), a pracę w ultra-niskich temperaturach rozwiązuje doborem modelu i
 * chemii ogniwa, nie deratingiem. Mnożenie stanu naładowania przez „współczynnik
 * temperaturowy" i porównywanie do progu procentowego nie ma pokrycia w danych
 * producenta — i myli dwie różne wielkości. Szczegóły: {@code REVALIDATION_PLANNER_W4_SUPPLEMENT.md} §2-§3.
 *
 * <h2>Czego producent nie dostarcza</h2>
 * Potwierdzone na sprzęcie (174 T, sierpień 2026): oprogramowanie Testo pokazuje
 * stan baterii jako <i>liczbę dni referencyjnych</i> — 388 dni przy 77,6 %
 * naładowania i katalogowych 500 dniach — i wskazanie to <b>nie zmienia się</b>
 * po zmianie interwału. Producent nie publikuje więc żadnej zależności czasu
 * pracy od cyklu pomiarowego; człon {@code cycleFactor} w
 * {@code replaceableBatteryBudget} jest <b>naszym własnym, zachowawczym
 * założeniem</b>, a nie daną katalogową. Zakres alarmowy komory (np. 2…8 °C) nie
 * wpływa na czas pracy ani u producenta, ani u nas.
 * <p>
 * Z tego samego powodu <b>nie modelujemy deratingu temperaturowego</b>. Wcześniej
 * powstawało tu zastrzeżenie „praca poniżej temperatury referencyjnej”, ale
 * dotyczyło każdej chłodziarki i każdej zamrażarki, nie zmieniało wyniku i nie
 * docierało do żadnego odbiorcy — w dokumentacji walidacyjnej wyglądało na
 * zabezpieczenie, którego faktycznie nie było. Temperatura wchodzi do reguły W4
 * wyłącznie jako bramka W4a (czy urządzenie w tej komorze w ogóle pracuje);
 * {@code batteryLifeRefTempC} pozostaje daną informacyjną karty rejestratora.
 */
@Service
public class HardwareCapacityService {

    private static final double MINUTES_PER_DAY = 1440.0;

    private final double batterySafetyFactor;

    public HardwareCapacityService(
            @Value("${app.planner.w4.battery-safety-factor:1.5}") double batterySafetyFactor) {
        this.batterySafetyFactor = batterySafetyFactor;
    }

    /**
     * Ocenia limity sprzętowe bez rzucania wyjątku — planer musi móc odrzucić
     * kandydata i szukać dalej, a nie przerwać całą alokację na pierwszym
     * niepasującym rejestratorze.
     *
     * @param missionStart dzień rozpoczęcia badania; punkt odniesienia wieku
     *                     baterii i zużytego limitu loggerów jednorazowych
     */
    public HardwareBudget evaluate(ThermoRecorder recorder, ProcedureClassConfig config,
                                   CoolingChamber chamber, LocalDate missionStart) {
        List<HardwareViolation> violations = new ArrayList<>();

        ThermoRecorderModel model = recorder.getModel();
        if (model == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Rejestrator S/N:%s nie ma przypisanego modelu — reguły W4 nie da się ocenić",
                    recorder.getSerialNumber())));
            return new HardwareBudget(violations, Double.NaN, Double.NaN, Double.NaN,
                    HardwareBudget.BindingConstraint.UNKNOWN);
        }

        double missionDays = missionMinutes(config) / MINUTES_PER_DAY;

        checkOperatingRange(recorder, model, chamber, violations);
        double memoryLimitDays = checkMemory(recorder, model, config, violations);
        double batteryLimitDays =
                checkBattery(recorder, model, config, chamber, missionStart, missionDays, violations);

        return new HardwareBudget(violations, memoryLimitDays, batteryLimitDays, missionDays,
                binding(memoryLimitDays, batteryLimitDays));
    }

    /**
     * Wariant rzucający — dla ręcznej podmiany sprzętu w zaplanowanym zadaniu,
     * gdzie operator wskazał konkretny rejestrator i oczekuje przyczyny odmowy.
     */
    public void require(ThermoRecorder recorder, ProcedureClassConfig config,
                        CoolingChamber chamber, LocalDate missionStart) {
        HardwareBudget budget = evaluate(recorder, config, chamber, missionStart);
        if (!budget.isAcceptable()) {
            throw exceptionFor(budget.firstViolation());
        }
    }

    /**
     * Przekłada naruszenie na wyjątek alokacji — silnik planera zapisuje z niego
     * {@code resourceStatus} i {@code shortageReason} bez rozpoznawania typu.
     */
    public RecorderAllocationException exceptionFor(HardwareViolation violation) {
        return switch (violation.rule()) {
            case OPERATING_RANGE -> new RecorderOutOfOperatingRangeException(violation.message());
            case MEMORY -> new InsufficientRecorderMemoryException(violation.message(),
                    (int) violation.required(), (int) violation.available());
            case BATTERY -> new InsufficientBatteryLevelException(violation.message(),
                    violation.required(), violation.available());
            case DATA_INCOMPLETE -> new HardwareDataIncompleteException(violation.message());
        };
    }

    /**
     * Pełny czas pracy rejestratora: od umieszczenia w komorze, przez
     * stabilizację i pomiar, po nieprzekraczalny termin odczytu.
     * <p>
     * Krok 3 nie zużywa pamięci (start jest opóźniony — reguła W3), ale zużywa
     * energię, więc do budżetu baterii wchodzi tak samo jak sam pomiar.
     */
    private long missionMinutes(ProcedureClassConfig config) {
        return (long) config.getStep2PlacementMinutes()
                + 60L * config.getStep3StabHours()
                + (long) config.getStep4IntervalMinutes() * config.getStep4SampleCount()
                + 60L * config.getStep5ReadoutBufferHours();
    }

    private void checkOperatingRange(ThermoRecorder recorder, ThermoRecorderModel model,
                                     CoolingChamber chamber, List<HardwareViolation> violations) {
        if (!model.hasHardwareSpecification()) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Model %s nie ma w kartotece zakresu pracy — uzupełnij dane katalogowe producenta "
                            + "przed planowaniem badania rejestratorem S/N:%s",
                    model.getName(), recorder.getSerialNumber())));
            return;
        }

        Double chamberMin = chamber.getEffectiveMinTempLimit();
        Double chamberMax = chamber.getEffectiveMaxTempLimit();
        if (chamberMin == null || chamberMax == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Komora %s nie ma skonfigurowanych limitów temperatury — reguły W4 nie da się ocenić",
                    chamber.getChamberName())));
            return;
        }

        if (chamberMin < model.getMinOperatingTempC() || chamberMax > model.getMaxOperatingTempC()) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.OPERATING_RANGE, String.format(
                    "Rejestrator S/N:%s (%s) pracuje w zakresie %.1f…%.1f°C, a komora %s wymaga %.1f…%.1f°C",
                    recorder.getSerialNumber(), model.getName(),
                    model.getMinOperatingTempC(), model.getMaxOperatingTempC(),
                    chamber.getChamberName(), chamberMin, chamberMax)));
        }
    }

    /**
     * @return maksymalny czas rejestracji wynikający z pamięci [dni]
     * <p>
     * Liczba <b>interwałów</b> to o jeden mniej niż liczba próbek — pierwszy
     * odczyt zapada w chwili startu. Tak samo liczy oprogramowanie producenta:
     * 174 T (16 000 próbek) przy 1 min pokazuje „11d 2h 39m" = 15 999 min,
     * a przy 10 min „111d 2h 30m" = 15 999 × 10 min. Bez odjęcia jedynki
     * zawyżalibyśmy limit o cały interwał, co przy Δt = 24 h jest całą dobą.
     */
    private double checkMemory(ThermoRecorder recorder, ThermoRecorderModel model,
                               ProcedureClassConfig config, List<HardwareViolation> violations) {
        int perChannel = model.getSampleCapacityPerChannel();
        int required = config.getStep4SampleCount();
        double memoryLimitDays = Math.max(0, perChannel - 1)
                * (double) config.getStep4IntervalMinutes() / MINUTES_PER_DAY;

        if (required > perChannel) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.MEMORY, String.format(
                    "Rejestrator S/N:%s (%s) ma %d próbek na kanał (%d / %d kanałów), a procedura wymaga %d "
                            + "(interwał %d min → limit pamięci %.1f dnia)",
                    recorder.getSerialNumber(), model.getName(), perChannel,
                    model.getSampleCapacity(), model.getChannelCount(), required,
                    config.getStep4IntervalMinutes(), memoryLimitDays), required, perChannel));
        }
        return memoryLimitDays;
    }

    /** @return dostępny budżet energii [dni] albo {@code NaN}, gdy nieznany */
    private double checkBattery(ThermoRecorder recorder, ThermoRecorderModel model,
                                ProcedureClassConfig config, CoolingChamber chamber,
                                LocalDate missionStart, double missionDays,
                                List<HardwareViolation> violations) {
        BatteryBasis basis = Boolean.FALSE.equals(model.getBatteryReplaceable())
                ? disposableLoggerBudget(recorder, model, missionStart, violations)
                : replaceableBatteryBudget(recorder, model, config, missionStart, violations);

        if (Double.isNaN(basis.availableDays())) {
            return Double.NaN;
        }

        double allowedDays = basis.availableDays() / batterySafetyFactor;
        if (missionDays > allowedDays) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.BATTERY, String.format(
                    "Rejestrator S/N:%s (%s): %s; dopuszczalne %.1f dnia przy zapasie ×%.1f, "
                            + "a badanie w komorze %s trwa %.1f dnia",
                    recorder.getSerialNumber(), model.getName(), basis.derivation(), allowedDays,
                    batterySafetyFactor, chamber.getChamberName(), missionDays),
                    missionDays, basis.availableDays()));
        }
        return basis.availableDays();
    }

    /**
     * Budżet energii wraz z jego wyprowadzeniem.
     * <p>
     * Operator widzi w oprogramowaniu producenta stan ogniwa wyrażony w dniach
     * (np. „388 dni”) i porównuje go z naszą liczbą. Bez pokazania, skąd bierze
     * się różnica — zachowawcze przeliczenie na cykl pomiarowy i zapas
     * bezpieczeństwa — rozbieżność wygląda na błąd aplikacji. Dlatego komunikat
     * odrzucenia niesie całe wyprowadzenie, a nie sam wynik.
     *
     * @param availableDays dostępny budżet [dni]; {@code NaN} gdy nieznany
     * @param derivation    człon komunikatu opisujący, z czego ta liczba wynika
     */
    private record BatteryBasis(double availableDays, String derivation) {

        static final BatteryBasis UNKNOWN = new BatteryBasis(Double.NaN, null);
    }

    /**
     * Loggery jednorazowe (testo 184 T1/T2) nie mają wymiany baterii ani
     * użytecznego wskaźnika naładowania — producent podaje sztywny limit pracy
     * urządzenia. Brak {@code firstActivationDate} czytamy jako „jeszcze nie
     * uruchomiony", czyli pełny budżet.
     */
    private BatteryBasis disposableLoggerBudget(ThermoRecorder recorder, ThermoRecorderModel model,
                                                LocalDate missionStart, List<HardwareViolation> violations) {
        Integer limitDays = model.getOperatingDurationDays();
        if (limitDays == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Model %s ma baterię niewymienną, ale w kartotece brak limitu pracy urządzenia",
                    model.getName())));
            return BatteryBasis.UNKNOWN;
        }

        LocalDate activation = recorder.getFirstActivationDate();
        long usedDays = activation == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(activation, missionStart));
        double remaining = Math.max(0, limitDays - usedDays);

        return new BatteryBasis(remaining, String.format(
                "z limitu pracy urządzenia %d dni zużyto %d, zostaje %.1f dnia",
                limitDays, usedDays, remaining));
    }

    private BatteryBasis replaceableBatteryBudget(ThermoRecorder recorder, ThermoRecorderModel model,
                                                  ProcedureClassConfig config, LocalDate missionStart,
                                                  List<HardwareViolation> violations) {
        if (model.getBatteryLifeDays() == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Model %s nie ma w kartotece katalogowej żywotności baterii", model.getName())));
            return BatteryBasis.UNKNOWN;
        }

        Integer soc = recorder.getLastBatteryLevelPercent();
        if (soc == null || soc < 0) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Rejestrator S/N:%s nie ma odczytanego stanu baterii — zczytaj urządzenie w stacji Testo USB "
                            + "przed zaplanowaniem badania", recorder.getSerialNumber())));
            return BatteryBasis.UNKNOWN;
        }

        checkBatteryAge(recorder, model, missionStart, violations);

        // Stan ogniwa w dniach referencyjnych — ta sama wielkość, którą pokazuje
        // oprogramowanie producenta (174 T: 77,6 % z 500 dni = 388 dni), więc
        // operator może zestawić ją z tym, co widzi w stacji Testo.
        double referenceRuntimeDays = model.getBatteryLifeDays() * (soc / 100.0);

        // ZAŁOŻENIE WŁASNE, nie dana producenta. Testo podaje żywotność przy
        // cyklu referencyjnym (15 min) i nie publikuje zależności od interwału —
        // wskazanie stanu baterii w oryginalnym oprogramowaniu jest identyczne
        // przy 1 min i przy 10 min. Skracamy budżet przy gęstszym próbkowaniu,
        // bo każdy pomiar kosztuje energię; przy rzadszym nie zakładamy zysku,
        // bo pobór spoczynkowy (LCD, zegar, NFC) płynie niezależnie od pomiarów.
        // Współczynnik jest zachowawczy w obie strony i czeka na pomiar
        // empiryczny — patrz REVALIDATION_PLANNER_W4_SUPPLEMENT.md §7.
        int refCycle = model.getBatteryLifeRefCycleMin() != null && model.getBatteryLifeRefCycleMin() > 0
                ? model.getBatteryLifeRefCycleMin()
                : 15;
        double cycleFactor = Math.min(1.0, (double) config.getStep4IntervalMinutes() / refCycle);
        double availableDays = referenceRuntimeDays * cycleFactor;

        return new BatteryBasis(availableDays, String.format(
                "stan ogniwa %.1f dnia (%d %% z %d dni katalogowych), po zachowawczym przeliczeniu "
                        + "na cykl %d min → %.1f dnia",
                referenceRuntimeDays, soc, model.getBatteryLifeDays(),
                config.getStep4IntervalMinutes(), availableDays));
    }

    private void checkBatteryAge(ThermoRecorder recorder, ThermoRecorderModel model,
                                 LocalDate missionStart, List<HardwareViolation> violations) {
        if (recorder.getBatteryReplacementDate() == null || model.getBatteryShelfLifeMonths() == null) {
            return;
        }
        LocalDate expiry = recorder.getBatteryReplacementDate().plusMonths(model.getBatteryShelfLifeMonths());
        if (!missionStart.isBefore(expiry)) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.BATTERY, String.format(
                    "Rejestrator S/N:%s ma baterię zamontowaną %s — dopuszczalny wiek %d mies. mija %s, "
                            + "przed badaniem wymagana wymiana",
                    recorder.getSerialNumber(), recorder.getBatteryReplacementDate(),
                    model.getBatteryShelfLifeMonths(), expiry)));
        }
    }

    private HardwareBudget.BindingConstraint binding(double memoryLimitDays, double batteryLimitDays) {
        if (Double.isNaN(batteryLimitDays)) {
            return HardwareBudget.BindingConstraint.UNKNOWN;
        }
        return memoryLimitDays <= batteryLimitDays / batterySafetyFactor
                ? HardwareBudget.BindingConstraint.MEMORY
                : HardwareBudget.BindingConstraint.BATTERY;
    }
}