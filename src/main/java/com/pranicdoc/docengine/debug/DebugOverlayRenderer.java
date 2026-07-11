package com.pranicdoc.docengine.debug;

import com.pranicdoc.docengine.output.DocumentResult;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Renders detected boxes/labels/relationships over the original PDF, colored per detector,
 * with confidence shown as opacity or a numeric label. Roadmap Phase 1 (basic), Phase 4
 * (detector colors once detectors exist), Phase 5 (template-match visualization).
 */
public interface DebugOverlayRenderer {
    DebugExport render(PDDocument document, DocumentResult result, OverlayStyle style);
}
