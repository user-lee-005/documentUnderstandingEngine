package com.pranicdoc.docengine.debug;

import java.awt.Color;

/** Maps a 0..1 confidence score onto a red (low) -> yellow -> green (high) hue ramp. */
public final class ConfidenceColorScale {

    private static final float MAX_HUE = 0.33f; // ~120 degrees = green; 0 degrees = red

    private ConfidenceColorScale() {
    }

    public static Color colorFor(double confidence) {
        double clamped = Math.max(0.0, Math.min(1.0, confidence));
        float hue = (float) (clamped * MAX_HUE);
        return Color.getHSBColor(hue, 0.85f, 0.9f);
    }
}
