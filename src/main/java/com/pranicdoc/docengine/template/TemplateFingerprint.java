package com.pranicdoc.docengine.template;

import com.pranicdoc.docengine.classify.PageDimensions;

import java.util.List;

/**
 * Deliberately NOT filename- or raw-byte-hash based — two scans of the same form at
 * different JPEG quality should fingerprint identically. structuralHash is computed from
 * the Stage 3 layout tree shape (quantized bounding boxes + node-type sequence), robust to
 * minor rendering differences but sensitive to actual layout changes. Roadmap Phase 5.
 */
public record TemplateFingerprint(
    String structuralHash,
    List<TextAnchor> anchors,
    List<LogoRegion> logoPositions,
    List<PageDimensions> pageDimensions
) {

    public double similarityTo(TemplateFingerprint other) {
        throw new UnsupportedOperationException("Fingerprint similarity scoring is not implemented yet — see Roadmap Phase 5");
    }
}
