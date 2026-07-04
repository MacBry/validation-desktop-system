package com.mac.bry.desktop.model.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mapowanie pozycji GridPosition na znormalizowane współrzędne 3D
 * w układzie komory: x=[0,1] lewo→prawo, y=[0,1] przód→tył, z=[0,1] dół→góra
 * (BA-EXC002 §2.2).
 */
public final class GridPositionCoordinates {

    private static final Map<GridPosition, double[]> COORDS = new EnumMap<>(GridPosition.class);

    static {
        //                                                     x    y    z
        COORDS.put(GridPosition.TOP_FRONT_LEFT,     new double[]{0.0, 0.0, 1.0});
        COORDS.put(GridPosition.TOP_FRONT_RIGHT,    new double[]{1.0, 0.0, 1.0});
        COORDS.put(GridPosition.TOP_BACK_LEFT,      new double[]{0.0, 1.0, 1.0});
        COORDS.put(GridPosition.TOP_BACK_RIGHT,     new double[]{1.0, 1.0, 1.0});
        COORDS.put(GridPosition.BOTTOM_FRONT_LEFT,  new double[]{0.0, 0.0, 0.0});
        COORDS.put(GridPosition.BOTTOM_FRONT_RIGHT, new double[]{1.0, 0.0, 0.0});
        COORDS.put(GridPosition.BOTTOM_BACK_LEFT,   new double[]{0.0, 1.0, 0.0});
        COORDS.put(GridPosition.BOTTOM_BACK_RIGHT,  new double[]{1.0, 1.0, 0.0});
    }

    private GridPositionCoordinates() {
    }

    public static double[] getCoordinates(GridPosition pos) {
        return COORDS.get(pos).clone();
    }
}
