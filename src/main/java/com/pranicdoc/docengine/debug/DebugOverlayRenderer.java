package com.pranicdoc.docengine.debug;

import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.output.DocumentResult;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;

/**
 * Renders every merged detection candidate over the original PDF, colored by confidence
 * (ConfidenceColorScale), with a caption naming the detector/type/score. Takes the pre-resolution
 * candidate pool, not just DocumentResult's final fields — that's the whole point of a debug view.
 */
public interface DebugOverlayRenderer {
    DebugExport render(PDDocument document, List<DetectionCandidate> candidates, DocumentResult result, OverlayStyle style) throws IOException;
}
