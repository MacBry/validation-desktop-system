package com.mac.bry.desktop.service.hotspot;

public final class AbsMinStrategy implements ExtremeDetectionStrategy {
    @Override public String methodCode()           { return "ABS_MIN_COLDSPOT"; }
    @Override public String displayName()          { return "Absolute Minimum (worst-case)"; }
    @Override public boolean isHotspot()           { return false; }
    @Override public SensorStats.StatField field() { return SensorStats.StatField.ABS_MIN; }
}
