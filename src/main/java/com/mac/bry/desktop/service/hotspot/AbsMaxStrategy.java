package com.mac.bry.desktop.service.hotspot;

public final class AbsMaxStrategy implements ExtremeDetectionStrategy {
    @Override public String methodCode()           { return "ABS_MAX_HOTSPOT"; }
    @Override public String displayName()          { return "Absolute Maximum (worst-case)"; }
    @Override public boolean isHotspot()           { return true; }
    @Override public SensorStats.StatField field() { return SensorStats.StatField.ABS_MAX; }
}
