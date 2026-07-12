package com.pranicdoc.docengine.debug;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceColorScaleTest {

    @Test
    void lowConfidenceIsRed() {
        Color color = ConfidenceColorScale.colorFor(0.0);
        float hue = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
        assertEquals(0.0f, hue, 0.01f);
    }

    @Test
    void highConfidenceIsGreen() {
        Color color = ConfidenceColorScale.colorFor(1.0);
        float hue = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
        assertEquals(0.33f, hue, 0.01f);
    }

    @Test
    void midConfidenceIsBetweenRedAndGreen() {
        Color low = ConfidenceColorScale.colorFor(0.0);
        Color mid = ConfidenceColorScale.colorFor(0.5);
        Color high = ConfidenceColorScale.colorFor(1.0);

        float hueLow = Color.RGBtoHSB(low.getRed(), low.getGreen(), low.getBlue(), null)[0];
        float hueMid = Color.RGBtoHSB(mid.getRed(), mid.getGreen(), mid.getBlue(), null)[0];
        float hueHigh = Color.RGBtoHSB(high.getRed(), high.getGreen(), high.getBlue(), null)[0];

        assertTrue(hueLow < hueMid);
        assertTrue(hueMid < hueHigh);
    }

    @Test
    void outOfRangeValuesAreClamped() {
        assertEquals(ConfidenceColorScale.colorFor(0.0), ConfidenceColorScale.colorFor(-5.0));
        assertEquals(ConfidenceColorScale.colorFor(1.0), ConfidenceColorScale.colorFor(5.0));
    }
}
