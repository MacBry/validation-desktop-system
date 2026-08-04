package com.mac.bry.desktop.dto;

import java.time.LocalDateTime;

/**
 * Metadane pojedynczego odczytu rejestratora — projekcja zamiast całej encji
 * {@code ThermoMeasurementSeries}, która ciągnie za sobą {@code @Lob rawHexDump}
 * (LONGTEXT z surową transmisją USB). Karta szczegółów potrzebuje sześciu pól,
 * nie megabajtów hexu.
 */
public record RecorderReadoutSummary(
        LocalDateTime importedAt,
        String importedBy,
        Integer batteryLevelPercent,
        Integer loggingIntervalMinutes,
        Integer measurementsCount,
        String chamberName) {
}