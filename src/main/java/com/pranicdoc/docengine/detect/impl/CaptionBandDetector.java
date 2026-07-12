package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Claims the writable band <b>beneath</b> each fieldset caption — the "EMAIL" /
 * "OCCUPATION" pattern where the caption sits on a divider line and the value is written in
 * the empty band below it. Those bands carry no geometry of their own (no rect, no
 * underline), so no primitive-driven detector can see them; this one derives them from the
 * captions themselves.
 *
 * <p>Runs against SECTION scopes (the same contract as {@link TableDetector}): the caption
 * and its value band usually land in <i>different</i> leaf rows, so a row-scoped detector
 * could never span both. Band width runs from the caption's left edge to the next caption
 * on the same visual row (minus a margin), else to the section's right edge.
 *
 * <p>Emitted as {@link CandidateType#WHITESPACE} — an unruled writable area — with the
 * caption text attached for Stage 6 pairing.
 */
public class CaptionBandDetector implements FieldCandidateDetector {

    public static final String ID = "caption-band-detector";

    /** One handwriting/value row: band claimed from captionBottom - HEIGHT up to captionBottom - GAP. */
    private static final double BAND_HEIGHT_PTS = 19.0;
    private static final double BAND_TOP_GAP_PTS = 2.0;
    private static final double NEXT_CAPTION_MARGIN_PTS = 4.0;
    private static final double SECTION_EDGE_MARGIN_PTS = 5.0;
    private static final double CONFIDENCE = 0.65;

    @Override
    public String detectorId() {
        return ID;
    }

    @Override
    public boolean isApplicable(PipelineContext ctx) {
        return true;
    }

    @Override
    public List<DetectionCandidate> detect(LayoutNode scope, PipelineContext ctx) {
        List<LayoutNode> rows = scope.children().stream().filter(n -> n.type() == LayoutNodeType.ROW).toList();
        if (rows.isEmpty()) {
            return List.of(); // leaf scope — this detector only makes sense with section context
        }

        List<DetectionCandidate> candidates = new ArrayList<>();
        for (LayoutNode row : rows) {
            List<TextLine> lines = row.attribute("textLines");
            if (lines == null) {
                continue;
            }
            for (TextLine line : lines) {
                emitBandsForLine(line, scope.box(), candidates);
            }
        }
        return candidates;
    }

    private static void emitBandsForLine(TextLine line, BoundingBox sectionBox, List<DetectionCandidate> out) {
        List<List<TextWord>> clusters = CaptionHeuristics.clusters(line);
        List<Caption> captions = new ArrayList<>();
        for (List<TextWord> cluster : clusters) {
            String text = CaptionHeuristics.textOf(cluster);
            if (!text.isEmpty() && CaptionHeuristics.isFieldsetCaption(cluster, text)) {
                captions.add(new Caption(text, BoundingBox.unionOf(cluster.stream().map(TextWord::box).toList())));
            }
        }
        for (int i = 0; i < captions.size(); i++) {
            Caption caption = captions.get(i);
            double right = i + 1 < captions.size()
                    ? captions.get(i + 1).box().x0() - NEXT_CAPTION_MARGIN_PTS
                    : sectionBox.x1() - SECTION_EDGE_MARGIN_PTS;
            if (right - caption.box().x0() < 10.0) {
                continue;
            }
            BoundingBox band = BoundingBox.of(
                    caption.box().x0(), caption.box().y0() - BAND_HEIGHT_PTS,
                    right, caption.box().y0() - BAND_TOP_GAP_PTS);
            out.add(new DetectionCandidate(ID, band, CandidateType.WHITESPACE, CONFIDENCE, line.page(),
                    Map.of("caption", caption.text())));
        }
    }

    private record Caption(String text, BoundingBox box) {
    }
}
