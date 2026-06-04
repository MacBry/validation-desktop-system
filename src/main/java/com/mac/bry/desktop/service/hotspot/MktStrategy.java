package com.mac.bry.desktop.service.hotspot;

public final class MktStrategy implements ExtremeDetectionStrategy {
    @Override public String methodCode()           { return "MKT_HOTSPOT"; }
    @Override public String displayName()          { return "Mean Kinetic Temperature (Arrhenius)"; }
    @Override public boolean isHotspot()           { return true; }
    @Override public SensorStats.StatField field() { return SensorStats.StatField.MKT; }
}
