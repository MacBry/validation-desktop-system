package com.mac.bry.desktop.service.hotspot;

public final class PercentileStrategy implements ExtremeDetectionStrategy {
    private final boolean isHotspot;

    public PercentileStrategy(boolean isHotspot) {
        this.isHotspot = isHotspot;
    }

    @Override
    public String methodCode() {
        return isHotspot ? "P99_HOTSPOT" : "P01_COLDSPOT";
    }

    @Override
    public String displayName() {
        return isHotspot ? "99th Percentile" : "1st Percentile";
    }

    @Override
    public boolean isHotspot() {
        return isHotspot;
    }

    @Override
    public SensorStats.StatField field() {
        return isHotspot ? SensorStats.StatField.P99 : SensorStats.StatField.P01;
    }
}
