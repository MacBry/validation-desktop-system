package com.mac.bry.desktop.service.hotspot;

public record SensorStats(
        String sensorId,
        double absMax,
        double absMin,
        double mean,
        double p99,
        double p01,
        double mkt,
        double tolHi,
        double tolLo
) {
    public double get(StatField field) {
        return switch (field) {
            case ABS_MAX -> absMax;
            case ABS_MIN -> absMin;
            case MEAN    -> mean;
            case P99     -> p99;
            case P01     -> p01;
            case MKT     -> mkt;
            case TOL_HI  -> tolHi;
            case TOL_LO  -> tolLo;
        };
    }

    public enum StatField {
        ABS_MAX, ABS_MIN, MEAN, P99, P01, MKT, TOL_HI, TOL_LO
    }
}
