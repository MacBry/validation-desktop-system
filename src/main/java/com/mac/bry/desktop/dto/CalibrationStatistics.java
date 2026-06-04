package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalibrationStatistics {
    private long valid;
    private long expiringSoon;
    private long expired;
}
