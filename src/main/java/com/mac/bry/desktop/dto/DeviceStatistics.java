package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatistics {
    private long totalDevices;
    private long totalChambers;
    private long validChambers;
    private long warningChambers;
    private long notValidatedChambers;
}
