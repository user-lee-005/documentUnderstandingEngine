package com.pranicdoc.docengine.debug;

import java.awt.Color;
import java.util.Map;

/** Maps detectorId -> overlay color, kept consistent across a run so the same detector always renders the same color. */
public record OverlayStyle(Map<String, Color> colorByDetectorId) {
}
