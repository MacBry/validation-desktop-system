package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefrostCycle {
    private String sensorName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double durationMinutes;
    private double maxTemperature;
    private double amplitude;
}
