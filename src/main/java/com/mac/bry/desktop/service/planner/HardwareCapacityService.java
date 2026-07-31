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
 */
@Service
public class HardwareCapacityService {

    private static final double MINUTES_PER_DAY = 1440.0;

    /**
     * Poniżej tej różnicy wobec temperatury referencyjnej specyfikacji nie
     * zgłaszamy zastrzeżenia — chodzi o realną pracę w chłodzie, nie o szum
     * zaokrągleń limitu komory.
     */
    private static final double REF_TEMP_TOLERANCE_C = 0.5;

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
        List<String> warnings = new ArrayList<>();

        ThermoRecorderModel model = recorder.getModel();
        if (model == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Rejestrator S/N:%s nie ma przypisanego modelu — reguły W4 nie da się ocenić",
                    recorder.getSerialNumber())));
            return new HardwareBudget(violations, warnings, Double.NaN, Double.NaN, Double.NaN,
                    HardwareBudget.BindingConstraint.UNKNOWN);
        }

        double missionDays = missionMinutes(config) / MINUTES_PER_DAY;

        checkOperatingRange(recorder, model, chamber, violations);
        double memoryLimitDays = checkMemory(recorder, model, config, violations);
        double batteryLimitDays =
                checkBattery(recorder, model, config, chamber, missionStart, missionDays, violations, warnings);

        return new HardwareBudget(violations, warnings, memoryLimitDays, batteryLimitDays, missionDays,
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

    /** @return maksymalny czas rejestracji wynikający z pamięci [dni] */
    private double checkMemory(ThermoRecorder recorder, ThermoRecorderModel model,
                               ProcedureClassConfig config, List<HardwareViolation> violations) {
        int perChannel = model.getSampleCapacityPerChannel();
        int required = config.getStep4SampleCount();
        double memoryLimitDays = perChannel * (double) config.getStep4IntervalMinutes() / MINUTES_PER_DAY;

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
                                List<HardwareViolation> violations, List<String> warnings) {
        double availableDays = Boolean.FALSE.equals(model.getBatteryReplaceable())
                ? disposableLoggerBudget(recorder, model, missionStart, violations)
                : replaceableBatteryBudget(recorder, model, config, chamber, missionStart, violations, warnings);

        if (Double.isNaN(availableDays)) {
            return Double.NaN;
        }

        double allowedDays = availableDays / batterySafetyFactor;
        if (missionDays > allowedDays) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.BATTERY, String.format(
                    "Rejestrator S/N:%s (%s): budżet energii %.1f dnia (dopuszczalne %.1f dnia przy zapasie ×%.1f), "
                            + "a badanie w komorze %s trwa %.1f dnia",
                    recorder.getSerialNumber(), model.getName(), availableDays, allowedDays,
                    batterySafetyFactor, chamber.getChamberName(), missionDays),
                    missionDays, availableDays));
        }
        return availableDays;
    }

    /**
     * Loggery jednorazowe (testo 184 T1/T2) nie mają wymiany baterii ani
     * użytecznego wskaźnika naładowania — producent podaje sztywny limit pracy
     * urządzenia. Brak {@code firstActivationDate} czytamy jako „jeszcze nie
     * uruchomiony", czyli pełny budżet.
     */
    private double disposableLoggerBudget(ThermoRecorder recorder, ThermoRecorderModel model,
                                          LocalDate missionStart, List<HardwareViolation> violations) {
        Integer limitDays = model.getOperatingDurationDays();
        if (limitDays == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Model %s ma baterię niewymienną, ale w kartotece brak limitu pracy urządzenia",
                    model.getName())));
            return Double.NaN;
        }

        LocalDate activation = recorder.getFirstActivationDate();
        long usedDays = activation == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(activation, missionStart));
        return Math.max(0, limitDays - usedDays);
    }

    private double replaceableBatteryBudget(ThermoRecorder recorder, ThermoRecorderModel model,
                                            ProcedureClassConfig config, CoolingChamber chamber,
                                            LocalDate missionStart,
                                            List<HardwareViolation> violations, List<String> warnings) {
        if (model.getBatteryLifeDays() == null) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Model %s nie ma w kartotece katalogowej żywotności baterii", model.getName())));
            return Double.NaN;
        }

        Integer soc = recorder.getLastBatteryLevelPercent();
        if (soc == null || soc < 0) {
            violations.add(new HardwareViolation(HardwareViolation.Rule.DATA_INCOMPLETE, String.format(
                    "Rejestrator S/N:%s nie ma odczytanego stanu baterii — zczytaj urządzenie w stacji Testo USB "
                            + "przed zaplanowaniem badania", recorder.getSerialNumber())));
            return Double.NaN;
        }

        checkBatteryAge(recorder, model, missionStart, violations);
        warnIfBelowReferenceTemperature(model, chamber, warnings);

        // Specyfikacja obowiązuje przy cyklu referencyjnym (15 min). Gęstsze
        // próbkowanie zużywa więcej energii; przy rzadszym nie zakładamy zysku,
        // bo pobór spoczynkowy (LCD, zegar, NFC) płynie niezależnie od pomiarów.
        int refCycle = model.getBatteryLifeRefCycleMin() != null && model.getBatteryLifeRefCycleMin() > 0
                ? model.getBatteryLifeRefCycleMin()
                : 15;
        double cycleFactor = Math.min(1.0, (double) config.getStep4IntervalMinutes() / refCycle);

        return model.getBatteryLifeDays() * cycleFactor * (soc / 100.0);
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

    /**
     * Producent podaje żywotność w jednym punkcie temperaturowym. Praca poniżej
     * niego (np. 174 T w zamrażarce -20 °C przy specyfikacji dla +25 °C) skraca
     * realny czas pracy o nieznaną wartość.
     * <p>
     * Świadomie <b>nie</b> jest to blokada: zablokowanie każdego mapowania
     * zamrażarki uniemożliwiłoby normalną pracę pracowni, a wartości deratingu
     * nie da się zmyślić. Zastrzeżenie idzie do Kierownika Walidacji — patrz
     * kwestia otwarta nr 1 w suplemencie W4.
     */
    private void warnIfBelowReferenceTemperature(ThermoRecorderModel model, CoolingChamber chamber,
                                                 List<String> warnings) {
        Double refTemp = model.getBatteryLifeRefTempC();
        Double chamberMin = chamber.getEffectiveMinTempLimit();
        if (refTemp == null || chamberMin == null || chamberMin >= refTemp - REF_TEMP_TOLERANCE_C) {
            return;
        }
        warnings.add(String.format(
                "Żywotność baterii modelu %s jest specyfikowana dla %.1f°C, a komora %s pracuje przy %.1f°C — "
                        + "budżet energii jest oszacowaniem i wymaga zatwierdzenia przez Kierownika Walidacji",
                model.getName(), refTemp, chamber.getChamberName(), chamberMin));
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