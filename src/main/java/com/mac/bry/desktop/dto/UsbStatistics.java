package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsbStatistics {
    private long totalOperations;
    private long reads;
    private long programs;
}
