package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlChartData {
    private List<Double> subgroupMeans;
    private List<Double> subgroupStdDevs;
    
    private double xBarCentralLine;
    private double xBarUcl;
    private double xBarLcl;
    
    private double sCentralLine;
    private double sUcl;
    private double sLcl;
}
