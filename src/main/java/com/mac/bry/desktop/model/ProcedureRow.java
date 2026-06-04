package com.mac.bry.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcedureRow {
    private String type;
    private String location;
    private String dateImported;
    private String sensors;
    private int measurementsCount;
    private String gxpStatus;
    private List<ThermoMeasurementSeries> associatedSeries;
    private CoolingDevice device;
    private CoolingChamber chamber;
}
