package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatistics {
    private RecorderStatistics recorders;
    private CalibrationStatistics calibrations;
    private UserStatistics users;
    private long departments;
    private long laboratories;
    private DeviceStatistics devices;
    private UsbStatistics usb;
}
