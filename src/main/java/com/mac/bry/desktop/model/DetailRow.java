package com.mac.bry.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DetailRow {
    private String positionName;
    private String serialNumber;
    private String minTemp;
    private String maxTemp;
    private String avgTemp;
    private String mktTemp;
    private String uncertainty;
    private String spikes;
    private String driftClassification;
}
