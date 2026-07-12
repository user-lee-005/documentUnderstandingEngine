package com.pranicdoc.docengine.debug;

/** Box color comes from confidence (see ConfidenceColorScale), not detector identity — detectorId is still visible via the caption text. */
public record OverlayStyle(float strokeWidth, boolean drawCaptions, float renderDpi) {

    public static OverlayStyle defaults() {
        return new OverlayStyle(1.5f, true, 150f);
    }
}
