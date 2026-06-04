package com.mac.bry.desktop.service.hotspot;

public final class TimeOverLimitStrategy implements ExtremeDetectionStrategy {
    private final boolean isHotspot;

    public TimeOverLimitStrategy(boolean isHotspot) {
        this.isHotspot = isHotspot;
    }

    @Override
    public String methodCode() {
        return isHotspot ? "TOL_HI_HOTSPOT" : "TOL_LO_COLDSPOT";
    }

    @Override
    public String displayName() {
        return isHotspot ? "Time Over Limit (>U)" : "Time Under Limit (<L)";
    }

    @Override
    public boolean isHotspot() {
        return isHotspot;
    }

    @Override
    public SensorStats.StatField field() {
        return isHotspot ? SensorStats.StatField.TOL_HI : SensorStats.StatField.TOL_LO;
    }

    @Override
    public boolean isDegenerate(SensorStats s) {
        return isHotspot ? s.tolHi() == 0.0 : s.tolLo() == 0.0;
    }
}
