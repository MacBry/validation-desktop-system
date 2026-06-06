package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityIndexes {
    private double cp;
    private double cpk;

    public boolean isHighlyCapable() {
        return cpk >= 1.33;
    }

    public boolean isAcceptable() {
        return cpk >= 1.0;
    }
}
