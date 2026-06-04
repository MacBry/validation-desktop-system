package com.mac.bry.desktop.service.hotspot;

public final class MeanStrategy implements ExtremeDetectionStrategy {
    private final boolean isHotspot;

    public MeanStrategy(boolean isHotspot) {
        this.isHotspot = isHotspot;
    }

    @Override
    public String methodCode() {
        return isHotspot ? "MEAN_HOTSPOT" : "MEAN_COLDSPOT";
    }

    @Override
    public String displayName() {
        return "Arithmetic Mean (steady-state)";
    }

    @Override
    public boolean isHotspot() {
        return isHotspot;
    }

    @Override
    public SensorStats.StatField field() {
        return isHotspot ? SensorStats.StatField.MEAN : SensorStats.StatField.MEAN;
    }
}
