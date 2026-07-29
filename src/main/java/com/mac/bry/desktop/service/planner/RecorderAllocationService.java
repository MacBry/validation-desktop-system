package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.PlannedTaskRecorderAssignmentRepository;
import com.mac.bry.desktop.repository.ThermoRecorderRepository;
import com.mac.bry.desktop.service.planner.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dobór rejestratorów do zadania walidacyjnego — reguły W2 (pojemność puli),
 * W5 (brak podwójnej rezerwacji), W1 i W8 (kwalifikacja metrologiczna,
 * delegowana do {@link MetrologicalQualificationService}) oraz pokrycie
 * minimalnej liczby punktów pomiarowych.
 * <p>
 * <b>Rezerwacja jest na poziomie urządzenia, nie kanału.</b> Wielokanałowy
 * rejestrator to jedna fizyczna sztuka wkładana do jednej komory — jego sondy
 * rozmieszcza się wewnątrz tej samej komory, więc nie da się przydzielić
 * kanału 1 do komory A, a kanału 2 do komory B. Wiersze
 * {@link PlannedTaskRecorderAssignment} są zapisem tego, które kanały posłużyły
 * za punkty pomiarowe, a nie jednostką podziału sprzętu.
 */
@Service
public class RecorderAllocationService {

    private final ThermoRecorderRepository recorderRepository;
    private final PlannedTaskRecorderAssignmentRepository assignmentRepository;
    private final MetrologicalQualificationService qualificationService;
    private final int logisticBufferHours;

    public RecorderAllocationService(ThermoRecorderRepository recorderRepository,
                                     PlannedTaskRecorderAssignmentRepository assignmentRepository,
                                     MetrologicalQualificationService qualificationService,
                                     @Value("${app.planner.logistic-buffer-hours:24}") int logisticBufferHours) {
        this.recorderRepository = recorderRepository;
        this.assignmentRepository = assignmentRepository;
        this.qualificationService = qualificationService;
        this.logisticBufferHours = logisticBufferHours;
    }

    /**
     * Przydziela zadaniu komplet rejestratorów albo nie przydziela nic.
     * <p>
     * Częściowa alokacja nie jest tworzona — badanie z niepełną obsadą i tak
     * nie mogłoby się odbyć, a zapisane rezerwacje blokowałyby sprzęt innym
     * zadaniom (ST-W2-01).
     *
     * @throws RecorderAllocationException gdy kompletu nie da się skompletować;
     *         wyjątek niesie przyczynę i propozycję kolejnego okna
     */
    @Transactional
    public List<PlannedTaskRecorderAssignment> allocateRecorders(PlannedValidationTask task,
                                                                 CoolingChamber chamber) {
        LocalDateTime reservedFrom = task.getPlannedStep1Time();
        LocalDateTime reservedUntil = task.getPlannedStep5ReadoutDeadline().plusHours(logisticBufferHours);
        LocalDate measurementEnd = task.getPlannedStep4MapEnd().toLocalDate();

        int requiredRecorders = task.getRequiredRecorderCount();
        int requiredPoints = chamber.getMinMeasurementPoints() != null ? chamber.getMinMeasurementPoints() : 0;

        Set<Long> busyRecorderIds = new HashSet<>(
                assignmentRepository.findBusyRecorderIds(reservedFrom, reservedUntil));

        List<ThermoRecorder> activeRecorders =
                recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE);

        List<Candidate> free = new ArrayList<>();
        int qualifiedButBusy = 0;
        int rejectedForCalibration = 0;
        int rejectedForRange = 0;

        for (ThermoRecorder recorder : activeRecorders) {
            List<Integer> qualifiedChannels = qualifiedChannelsOf(recorder, chamber, measurementEnd);
            if (qualifiedChannels.isEmpty()) {
                if (hasChannelRejectedOnlyForCalibration(recorder, chamber, measurementEnd)) {
                    rejectedForCalibration++;
                } else {
                    rejectedForRange++;
                }
                continue;
            }
            if (busyRecorderIds.contains(recorder.getId())) {
                qualifiedButBusy++;
                continue;
            }
            free.add(new Candidate(recorder, qualifiedChannels));
        }

        if (free.size() + qualifiedButBusy == 0) {
            throw noQualifiedRecorder(chamber, rejectedForCalibration, rejectedForRange, activeRecorders.size());
        }

        if (free.size() < requiredRecorders) {
            throw new InsufficientRecorderCapacityException(String.format(
                    "Komora %s wymaga %d rejestratorów, a w oknie %s – %s wolnych i zakwalifikowanych jest %d "
                            + "(zajętych, lecz zakwalifikowanych: %d)",
                    chamber.getChamberName(), requiredRecorders, reservedFrom, reservedUntil,
                    free.size(), qualifiedButBusy),
                    requiredRecorders, free.size(),
                    assignmentRepository.findEarliestRelease(reservedFrom, reservedUntil));
        }

        List<Candidate> selected = selectCovering(free, requiredRecorders, requiredPoints);
        int selectedChannels = selected.stream().mapToInt(c -> c.channels().size()).sum();

        if (selectedChannels < requiredPoints) {
            throw new InsufficientMeasurementPointsException(String.format(
                    "Komora %s (%s) wymaga min. %d punktów pomiarowych, a %d dostępnych rejestratorów daje %d kanałów",
                    chamber.getChamberName(), chamber.getVolumeCategoryDisplayName(),
                    requiredPoints, free.size(), totalChannels(free)),
                    requiredPoints, totalChannels(free),
                    assignmentRepository.findEarliestRelease(reservedFrom, reservedUntil));
        }

        return persistAssignments(task, selected, reservedFrom, reservedUntil);
    }

    /**
     * Zwalnia obsadę zadania — po zatwierdzeniu raportu albo przy rekalkulacji.
     */
    @Transactional
    public void releaseRecorders(PlannedValidationTask task) {
        assignmentRepository.deleteByPlannedTask(task);
        task.getRecorderAssignments().clear();
    }

    /**
     * Weryfikacja W5 dla konkretnego rejestratora — używana przy ręcznej
     * podmianie sprzętu w już zaplanowanym zadaniu.
     *
     * @throws RecorderDoubleBookingException gdy okno koliduje z istniejącą rezerwacją
     */
    public void requireNoDoubleBooking(ThermoRecorder recorder, int channelNumber,
                                       LocalDateTime from, LocalDateTime until) {
        List<PlannedTaskRecorderAssignment> collisions =
                assignmentRepository.findCollisions(recorder, channelNumber, from, until);
        if (!collisions.isEmpty()) {
            PlannedTaskRecorderAssignment first = collisions.get(0);
            throw new RecorderDoubleBookingException(String.format(
                    "Rejestrator %s kanał %d jest już zarezerwowany na okno %s – %s",
                    recorder.getSerialNumber(), channelNumber, first.getReservedFrom(), first.getReservedUntil()),
                    first.getReservedUntil());
        }
    }

    private List<Integer> qualifiedChannelsOf(ThermoRecorder recorder, CoolingChamber chamber,
                                              LocalDate measurementEnd) {
        int channelCount = recorder.getModel() != null && recorder.getModel().getChannelCount() != null
                ? recorder.getModel().getChannelCount()
                : 1;

        List<Integer> qualified = new ArrayList<>();
        for (int channel = 1; channel <= channelCount; channel++) {
            if (qualificationService.isQualified(recorder, channel, chamber, measurementEnd)) {
                qualified.add(channel);
            }
        }
        return qualified;
    }

    /**
     * Rozróżnia przyczynę odrzucenia: wygasłe wzorcowanie da się naprawić
     * kalibracją tego samego sprzętu, niedopasowany zakres — nie.
     */
    private boolean hasChannelRejectedOnlyForCalibration(ThermoRecorder recorder, CoolingChamber chamber,
                                                         LocalDate measurementEnd) {
        int channelCount = recorder.getModel() != null && recorder.getModel().getChannelCount() != null
                ? recorder.getModel().getChannelCount()
                : 1;

        for (int channel = 1; channel <= channelCount; channel++) {
            Calibration calibration = recorder.getLatestCalibrationForChannel(channel);
            if (calibration != null
                    && calibration.coversMaterialRange(chamber.getEffectiveMinTempLimit(),
                                                       chamber.getEffectiveMaxTempLimit())) {
                return true; // zakres pasuje, odpadł więc na ważności świadectwa
            }
        }
        return false;
    }

    /**
     * Wybiera rejestratory: najpierw do wymaganej liczby sztuk (R1), potem
     * dobiera kolejne, dopóki suma kanałów nie pokryje minimalnej liczby
     * punktów pomiarowych.
     */
    private List<Candidate> selectCovering(List<Candidate> free, int requiredRecorders, int requiredPoints) {
        List<Candidate> selected = new ArrayList<>(free.subList(0, Math.min(requiredRecorders, free.size())));
        int channels = selected.stream().mapToInt(c -> c.channels().size()).sum();

        for (int i = requiredRecorders; i < free.size() && channels < requiredPoints; i++) {
            selected.add(free.get(i));
            channels += free.get(i).channels().size();
        }
        return selected;
    }

    private List<PlannedTaskRecorderAssignment> persistAssignments(PlannedValidationTask task,
                                                                   List<Candidate> selected,
                                                                   LocalDateTime reservedFrom,
                                                                   LocalDateTime reservedUntil) {
        List<PlannedTaskRecorderAssignment> assignments = new ArrayList<>();
        for (Candidate candidate : selected) {
            for (Integer channel : candidate.channels()) {
                PlannedTaskRecorderAssignment assignment = PlannedTaskRecorderAssignment.builder()
                        .thermoRecorder(candidate.recorder())
                        .channelNumber(channel)
                        .reservedFrom(reservedFrom)
                        .reservedUntil(reservedUntil)
                        .build();
                task.addRecorderAssignment(assignment);
                assignments.add(assignment);
            }
        }
        assignmentRepository.saveAll(assignments);
        task.setResourceStatus(TaskResourceStatus.OK);
        task.setShortageReason(null);
        task.setSuggestedWindowStart(null);
        return assignments;
    }

    private RecorderAllocationException noQualifiedRecorder(CoolingChamber chamber,
                                                            int rejectedForCalibration,
                                                            int rejectedForRange,
                                                            int activeCount) {
        if (rejectedForCalibration > 0 && rejectedForRange == 0) {
            return new CalibrationExpiredException(String.format(
                    "Żaden z %d aktywnych rejestratorów nie ma świadectwa ważnego przez cały pomiar komory %s",
                    activeCount, chamber.getChamberName()));
        }
        return new MetrologicalRangeMismatchException(String.format(
                "Żaden z %d aktywnych rejestratorów nie pokrywa zakresu materiału w komorze %s "
                        + "(odrzucone: %d na zakresie, %d na ważności świadectwa)",
                activeCount, chamber.getChamberName(), rejectedForRange, rejectedForCalibration));
    }

    private int totalChannels(List<Candidate> candidates) {
        return candidates.stream().mapToInt(c -> c.channels().size()).sum();
    }

    /** Rejestrator wraz z kanałami, które przeszły kwalifikację metrologiczną. */
    private record Candidate(ThermoRecorder recorder, List<Integer> channels) {
    }
}