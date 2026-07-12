package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flags text runs that read as field labels rather than filled-in values.
 *
 * <p>Because the PDFBox stripper emits one {@link TextLine} per visual row, side-by-side
 * captions ("PATIENT'S NAME" and "ADDRESS LINE 1 & 2" on the same row) arrive glued into a
 * single long line. Detection therefore runs per <b>cluster</b> (see
 * {@link CaptionHeuristics#clusters}), and each cluster is scored independently.
 *
 * <p>Three patterns, by confidence:
 * <ul>
 *   <li><b>0.99</b> — colon-terminated ("Patient Name:")</li>
 *   <li><b>0.85</b> — fieldset caption: short ALL-CAPS run in a small font, the style that
 *       sits on a container border ("ZIP / PINCODE", "SYMPTOM 1"). Digits allowed.</li>
 *   <li><b>0.6</b> — legacy fallback: short digit-free run</li>
 * </ul>
 * Real label/value disambiguation is layered on in Stage 6.
 */
public class LabelDetector implements FieldCandidateDetector {

    public static final String ID = "label-detector";

    private static final int MAX_LABEL_WORDS = 4;
    private static final int MAX_LABEL_CHARS = 30;

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
        List<TextLine> lines = scope.attribute("textLines");
        if (lines == null) {
            return List.of();
        }

        List<DetectionCandidate> candidates = new ArrayList<>();
        for (TextLine line : lines) {
            for (List<TextWord> cluster : CaptionHeuristics.clusters(line)) {
                scoreCluster(cluster, line.page(), candidates);
            }
        }
        return candidates;
    }

    private static void scoreCluster(List<TextWord> cluster, int page, List<DetectionCandidate> out) {
        String text = CaptionHeuristics.textOf(cluster);
        if (text.isEmpty()) {
            return;
        }

        boolean endsWithColon = text.endsWith(":");
        boolean shortRun = cluster.size() <= MAX_LABEL_WORDS && text.length() <= MAX_LABEL_CHARS;
        boolean digitFree = text.chars().noneMatch(Character::isDigit);

        double confidence;
        if (endsWithColon && shortRun) {
            confidence = 0.99;
        } else if (CaptionHeuristics.isFieldsetCaption(cluster, text)) {
            confidence = 0.85;
        } else if (shortRun && digitFree) {
            confidence = 0.6;
        } else {
            return;
        }

        BoundingBox box = BoundingBox.unionOf(cluster.stream().map(TextWord::box).toList());
        double avgFontSize = cluster.stream().flatMap(w -> w.chars().stream())
                .mapToDouble(c -> c.fontSize()).average().orElse(0.0);
        out.add(new DetectionCandidate(ID, box, CandidateType.LABEL, confidence, page,
                Map.of("text", text, "fontSize", avgFontSize)));
    }
}
