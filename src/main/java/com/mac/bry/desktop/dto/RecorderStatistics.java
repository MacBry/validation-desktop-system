package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecorderStatistics {
    private long total;
    private long active;
    private long underCalibration;
    private long inactive;
}
