package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.repository.CalibrationRepository;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Dzienny digest powiadomień GxP: wygasające/przeterminowane świadectwa
 * wzorcowania rejestratorów oraz komory wymagające rewalidacji okresowej
 * (cykl roczny od ostatniego mapowania).
 * <p>
 * Wysyłany raz dziennie (cron, domyślnie 7:00) na adresy super administratorów,
 * wyłącznie gdy są pozycje do zgłoszenia. Każda wysyłka odkłada się w Access Logu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GxpNotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CalibrationRepository calibrationRepository;
    private final CoolingChamberRepository coolingChamberRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final AuditService auditService;

    /** Wyprzedzenie ostrzeżenia [dni] — spójne z progiem EXPIRING_SOON dashboardu. */
    @Value("${app.notifications.advance-days:30}")
    private int advanceDays;

    /** Długość cyklu rewalidacji komory [miesiące]. */
    @Value("${app.notifications.revalidation-cycle-months:12}")
    private int revalidationCycleMonths;

    @Value("${app.notifications.enabled:true}")
    private boolean enabled;

    /** Codziennie o godzinie z konfiguracji (domyślnie 7:00). */
    @Scheduled(cron = "${app.notifications.cron:0 0 7 * * *}")
    public void sendDailyDigest() {
        if (!enabled) {
            log.debug("Powiadomienia GxP wyłączone (app.notifications.enabled=false)");
            return;
        }
        try {
            Optional<String> digest = buildDigest(LocalDate.now());
            if (digest.isEmpty()) {
                log.info("Powiadomienia GxP: brak wygasających pozycji — digest pominięty.");
                return;
            }

            List<String> recipients = userService.getSuperAdminEmails();
            if (recipients.isEmpty()) {
                log.warn("Powiadomienia GxP: brak adresów super administratorów — digest niewysłany.");
                return;
            }

            emailService.sendMassEmail(recipients,
                    "Validation System — dzienny przegląd GxP (wzorcowania i rewalidacje)",
                    digest.get());
            auditService.logAccessEvent("SYSTEM", "GXP_NOTIFICATION",
                    "Wysłano dzienny digest GxP do " + recipients.size() + " odbiorców");
        } catch (Exception e) {
            log.error("Powiadomienia GxP: błąd podczas budowania/wysyłki digestu", e);
        }
    }

    /**
     * Buduje treść digestu dla podanej daty odniesienia.
     * {@code Optional.empty()} gdy nie ma nic do zgłoszenia — wtedy nie wysyłamy.
     */
    @Transactional(readOnly = true)
    public Optional<String> buildDigest(LocalDate today) {
        LocalDate threshold = today.plusDays(advanceDays);

        List<Calibration> expiring = calibrationRepository.findLatestExpiringUntil(threshold);
        List<CoolingChamber> dueChambers = findChambersDueForRevalidation(today, threshold);

        if (expiring.isEmpty() && dueChambers.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Dzienny przegląd GxP — stan na ").append(today.format(DATE_FMT)).append("\n");
        sb.append("Próg ostrzeżenia: ").append(advanceDays).append(" dni\n\n");

        if (!expiring.isEmpty()) {
            sb.append("== ŚWIADECTWA WZORCOWANIA (").append(expiring.size()).append(") ==\n");
            for (Calibration c : expiring) {
                long days = ChronoUnit.DAYS.between(today, c.getValidUntil());
                String status = days < 0
                        ? "PRZETERMINOWANE od " + Math.abs(days) + " dni"
                        : "wygasa za " + days + " dni";
                sb.append(String.format("- Rejestrator S/N %s | świadectwo %s | ważne do %s | %s%n",
                        c.getThermoRecorder().getSerialNumber(),
                        c.getCertificateNumber(),
                        c.getValidUntil().format(DATE_FMT),
                        status));
            }
            sb.append("\n");
        }

        if (!dueChambers.isEmpty()) {
            sb.append("== KOMORY DO REWALIDACJI (").append(dueChambers.size()).append(") ==\n");
            for (CoolingChamber ch : dueChambers) {
                String device = ch.getCoolingDevice() != null
                        ? ch.getCoolingDevice().getName() + " (nr inw. "
                          + ch.getCoolingDevice().getInventoryNumber() + ")"
                        : "?";
                if (ch.getLastMappingDate() == null) {
                    sb.append(String.format("- %s / komora \"%s\" | BRAK MAPOWANIA — wymagana kwalifikacja%n",
                            device, ch.getChamberName()));
                } else {
                    LocalDate due = ch.getLastMappingDate().plusMonths(revalidationCycleMonths);
                    long days = ChronoUnit.DAYS.between(today, due);
                    String status = days < 0
                            ? "ZALEGŁA od " + Math.abs(days) + " dni"
                            : "termin za " + days + " dni";
                    sb.append(String.format(
                            "- %s / komora \"%s\" | ostatnie mapowanie %s | rewalidacja do %s | %s%n",
                            device, ch.getChamberName(),
                            ch.getLastMappingDate().format(DATE_FMT),
                            due.format(DATE_FMT), status));
                }
            }
            sb.append("\n");
        }

        sb.append("Szczegóły w aplikacji Validation System (moduły Rejestratory i Komory chłodnicze).");
        return Optional.of(sb.toString());
    }

    /**
     * Komory z wymaganym mapowaniem, których termin rewalidacji
     * (ostatnie mapowanie + cykl) mija do daty progu — lub bez żadnego mapowania.
     */
    private List<CoolingChamber> findChambersDueForRevalidation(LocalDate today, LocalDate threshold) {
        return coolingChamberRepository.findAll().stream()
                .filter(CoolingChamber::isMappingRequired)
                .filter(ch -> ch.getLastMappingDate() == null
                        || !ch.getLastMappingDate().plusMonths(revalidationCycleMonths).isAfter(threshold))
                .toList();
    }
}
