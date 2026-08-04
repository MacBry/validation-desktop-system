package com.mac.bry.desktop.service;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.dto.RecorderDetailProperty;
import com.mac.bry.desktop.dto.RecorderReadoutSummary;
import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.repository.CalibrationRepository;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Składa kartę szczegółów rejestratora — wszystko, co system o nim wie, w jednej
 * płaskiej liście wierszy gotowych do wyświetlenia.
 * <p>
 * Dane pochodzą z czterech źródeł: kartoteki egzemplarza, kartoteki modelu
 * (dane katalogowe producenta, W4), świadectw wzorcowania i historii odczytów.
 * Brak danych jest zawsze jawnym tekstem, nigdy pustą komórką — operator musi
 * odróżnić „nie wiemy" od „zero", bo dla reguły W4 to dwie różne sytuacje.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecorderDetailsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** Domyślny cykl referencyjny kart katalogowych Testo [min]. */
    private static final int DEFAULT_REF_CYCLE_MIN = 15;

    private final ThermoRecorderService recorderService;
    private final CalibrationRepository calibrationRepository;
    private final ThermoMeasurementSeriesRepository seriesRepository;

    @Transactional(readOnly = true)
    public List<RecorderDetailProperty> buildDetails(ThermoRecorder recorder) {
        List<RecorderDetailProperty> rows = new ArrayList<>();
        if (recorder == null) {
            return rows;
        }

        appendIdentification(rows, recorder);
        appendModelSpecification(rows, recorder.getModel());
        appendBattery(rows, recorder);
        appendCalibration(rows, recorder);
        appendUsage(rows, recorder);

        log.debug("Zbudowano kartę szczegółów rejestratora {} ({} wierszy)",
                recorder.getSerialNumber(), rows.size());
        return rows;
    }

    // --- Sekcje -------------------------------------------------------------

    private void appendIdentification(List<RecorderDetailProperty> rows, ThermoRecorder recorder) {
        String section = I18n.t("recorderdetails.section.identification");
        add(rows, section, "recorderdetails.serialNumber", recorder.getSerialNumber());
        add(rows, section, "recorderdetails.model",
                recorder.getModel() != null ? recorder.getModel().getName() : null);
        add(rows, section, "recorderdetails.status",
                recorder.getStatus() != null ? recorder.getStatus().getDisplayName() : null);
        add(rows, section, "recorderdetails.resolution",
                recorder.getResolution() != null ? recorder.getResolution().toPlainString() + " °C" : null);
        add(rows, section, "recorderdetails.department",
                recorder.getDepartment() != null ? recorder.getDepartment().getName() : null);
        add(rows, section, "recorderdetails.laboratory",
                recorder.getLaboratory() != null ? recorder.getLaboratory().getName() : null);
    }

    private void appendModelSpecification(List<RecorderDetailProperty> rows, ThermoRecorderModel model) {
        String section = I18n.t("recorderdetails.section.model");
        if (model == null) {
            add(rows, section, "recorderdetails.modelMissing", null);
            return;
        }

        add(rows, section, "recorderdetails.channelCount", text(model.getChannelCount()));
        add(rows, section, "recorderdetails.sampleCapacity", text(model.getSampleCapacity()));
        add(rows, section, "recorderdetails.sampleCapacityPerChannel",
                String.valueOf(model.getSampleCapacityPerChannel()));
        add(rows, section, "recorderdetails.operatingRange", operatingRange(model));
        add(rows, section, "recorderdetails.batteryType", model.getBatteryType());
        add(rows, section, "recorderdetails.batteryReplaceable", yesNo(model.getBatteryReplaceable()));

        if (Boolean.FALSE.equals(model.getBatteryReplaceable())) {
            add(rows, section, "recorderdetails.operatingDurationDays", text(model.getOperatingDurationDays()));
        } else {
            add(rows, section, "recorderdetails.batteryLifeSpec", batteryLifeSpec(model));
            add(rows, section, "recorderdetails.batteryShelfLifeMonths", text(model.getBatteryShelfLifeMonths()));
        }
    }

    private void appendBattery(List<RecorderDetailProperty> rows, ThermoRecorder recorder) {
        String section = I18n.t("recorderdetails.section.battery");
        add(rows, section, "recorderdetails.batteryLevel",
                recorder.getLastBatteryLevelPercent() != null
                        ? recorder.getLastBatteryLevelPercent() + " %"
                        : null);
        add(rows, section, "recorderdetails.batteryReadAt", formatDateTime(recorder.getLastBatteryReadAt()));
        add(rows, section, "recorderdetails.batteryReplacementDate",
                formatDate(recorder.getBatteryReplacementDate()));
        add(rows, section, "recorderdetails.batteryExpiry", batteryExpiry(recorder));
        add(rows, section, "recorderdetails.firstActivationDate", formatDate(recorder.getFirstActivationDate()));
        add(rows, section, "recorderdetails.estimatedRuntime", estimatedRuntime(recorder));
    }

    private void appendCalibration(List<RecorderDetailProperty> rows, ThermoRecorder recorder) {
        String section = I18n.t("recorderdetails.section.calibration");
        add(rows, section, "recorderdetails.calibrationStatus", recorderService.getCalibrationStatus(recorder));

        List<Calibration> calibrations = recorder.getId() != null
                ? calibrationRepository.findByThermoRecorderIdOrderByCalibrationDateDesc(recorder.getId())
                : List.of();

        add(rows, section, "recorderdetails.calibrationCount", String.valueOf(calibrations.size()));

        Calibration latest = calibrations.isEmpty() ? null : calibrations.get(0);
        if (latest == null) {
            return;
        }
        add(rows, section, "recorderdetails.calibrationDate", formatDate(latest.getCalibrationDate()));
        add(rows, section, "recorderdetails.certificateNumber", latest.getCertificateNumber());
        add(rows, section, "recorderdetails.validUntil", withDaysLeft(latest.getValidUntil()));
        add(rows, section, "recorderdetails.calibratedRange", latest.getCalibratedRange());
        add(rows, section, "recorderdetails.certificateFile", latest.getCertificateFilePath());
    }

    private void appendUsage(List<RecorderDetailProperty> rows, ThermoRecorder recorder) {
        String section = I18n.t("recorderdetails.section.usage");
        if (recorder.getId() == null) {
            add(rows, section, "recorderdetails.readoutCount", "0");
            return;
        }

        add(rows, section, "recorderdetails.readoutCount",
                String.valueOf(seriesRepository.countByThermoRecorderId(recorder.getId())));

        List<RecorderReadoutSummary> last =
                seriesRepository.findReadoutSummaries(recorder.getId(), PageRequest.of(0, 1));
        if (last.isEmpty()) {
            add(rows, section, "recorderdetails.lastReadoutAt", null);
            return;
        }

        RecorderReadoutSummary readout = last.get(0);
        add(rows, section, "recorderdetails.lastReadoutAt", formatDateTime(readout.importedAt()));
        add(rows, section, "recorderdetails.lastReadoutBy", readout.importedBy());
        add(rows, section, "recorderdetails.lastReadoutChamber", readout.chamberName());
        add(rows, section, "recorderdetails.lastReadoutBattery",
                readout.batteryLevelPercent() != null && readout.batteryLevelPercent() >= 0
                        ? readout.batteryLevelPercent() + " %"
                        : null);
        add(rows, section, "recorderdetails.lastReadoutInterval",
                readout.loggingIntervalMinutes() != null ? readout.loggingIntervalMinutes() + " min" : null);
        add(rows, section, "recorderdetails.lastReadoutMeasurements", text(readout.measurementsCount()));
    }

    // --- Wartości wyliczane -------------------------------------------------

    private String operatingRange(ThermoRecorderModel model) {
        if (!model.hasHardwareSpecification()) {
            return null;
        }
        return String.format(I18n.getLocale(), "%.1f…%.1f °C",
                model.getMinOperatingTempC(), model.getMaxOperatingTempC());
    }

    private String batteryLifeSpec(ThermoRecorderModel model) {
        if (model.getBatteryLifeDays() == null) {
            return null;
        }
        // Temperatura referencyjna bywa nieuzupełniona w kartotece — wtedy podajemy
        // sam cykl, zamiast wstawiać w komunikat "null" albo zmyśloną wartość.
        if (model.getBatteryLifeRefTempC() == null) {
            return I18n.t("recorderdetails.value.batteryLifeSpecNoTemp",
                    model.getBatteryLifeDays(), refCycleMinutes(model));
        }
        return I18n.t("recorderdetails.value.batteryLifeSpec",
                model.getBatteryLifeDays(), refCycleMinutes(model), model.getBatteryLifeRefTempC());
    }

    /**
     * Termin ważności zamontowanego ogniwa — data wymiany powiększona o
     * dopuszczalny wiek z kartoteki modelu. Po przekroczeniu reguła W4 blokuje
     * planowanie, więc karta musi to pokazać wprost, a nie samą datą.
     */
    private String batteryExpiry(ThermoRecorder recorder) {
        ThermoRecorderModel model = recorder.getModel();
        if (recorder.getBatteryReplacementDate() == null || model == null
                || model.getBatteryShelfLifeMonths() == null) {
            return null;
        }
        LocalDate expiry = recorder.getBatteryReplacementDate().plusMonths(model.getBatteryShelfLifeMonths());
        return withDaysLeft(expiry);
    }

    /**
     * Referencyjny budżet pracy — świadomie NIE jest to prognoza dla konkretnego
     * badania. Realny czas zależy od cyklu pomiarowego i temperatury komory,
     * więc pełne wyliczenie robi {@code HardwareCapacityService} dopiero przy
     * planowaniu. Tutaj pokazujemy wartość przy cyklu referencyjnym producenta,
     * tą samą arytmetyką (żywotność katalogowa × stan naładowania).
     */
    private String estimatedRuntime(ThermoRecorder recorder) {
        ThermoRecorderModel model = recorder.getModel();
        if (model == null) {
            return null;
        }

        if (Boolean.FALSE.equals(model.getBatteryReplaceable())) {
            Integer limitDays = model.getOperatingDurationDays();
            if (limitDays == null) {
                return null;
            }
            LocalDate activation = recorder.getFirstActivationDate();
            if (activation == null) {
                return I18n.t("recorderdetails.value.runtimeNotStarted", limitDays);
            }
            long used = Math.max(0, ChronoUnit.DAYS.between(activation, LocalDate.now()));
            return I18n.t("recorderdetails.value.runtimeDisposable",
                    Math.max(0, limitDays - used), limitDays);
        }

        Integer soc = recorder.getLastBatteryLevelPercent();
        if (model.getBatteryLifeDays() == null || soc == null || soc < 0) {
            return null;
        }
        double days = model.getBatteryLifeDays() * (soc / 100.0);
        return I18n.t("recorderdetails.value.runtimeReference", days, refCycleMinutes(model));
    }

    private int refCycleMinutes(ThermoRecorderModel model) {
        return model.getBatteryLifeRefCycleMin() != null && model.getBatteryLifeRefCycleMin() > 0
                ? model.getBatteryLifeRefCycleMin()
                : DEFAULT_REF_CYCLE_MIN;
    }

    private String withDaysLeft(LocalDate date) {
        if (date == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
        return days < 0
                ? I18n.t("recorderdetails.value.dateExpired", date.format(DATE_FMT), -days)
                : I18n.t("recorderdetails.value.dateWithDaysLeft", date.format(DATE_FMT), days);
    }

    // --- Pomocnicze ---------------------------------------------------------

    private void add(List<RecorderDetailProperty> rows, String section, String labelKey, String value) {
        rows.add(new RecorderDetailProperty(section, I18n.t(labelKey), display(value)));
    }

    private String display(String value) {
        return value == null || value.isBlank() ? I18n.t("recorderdetails.value.none") : value;
    }

    private String text(Number value) {
        return value == null ? null : String.valueOf(value);
    }

    private String yesNo(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? I18n.t("recorderdetails.value.yes") : I18n.t("recorderdetails.value.no");
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FMT);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FMT);
    }
}