package com.mac.bry.desktop.dto;

import java.time.LocalDateTime;

/**
 * Metadane pojedynczego odczytu rejestratora — projekcja zamiast całej encji
 * {@code ThermoMeasurementSeries}, która ciągnie za sobą {@code @Lob rawHexDump}
 * (LONGTEXT z surową transmisją USB). Karta szczegółów potrzebuje kilku pól,
 * nie megabajtów hexu.
 *
 * @param batteryRemainingDays pozostały czas pracy baterii [dni] z ramki
 *                             {@code ab010a}; {@code null} dla serii sprzed
 *                             korekty protokołu (2026-08-05) i dla źródeł
 *                             nieraportujących baterii
 */
public record RecorderReadoutSummary(
        LocalDateTime importedAt,
        String importedBy,
        Integer batteryRemainingDays,
        Integer loggingIntervalMinutes,
        Integer measurementsCount,
        String chamberName) {
}