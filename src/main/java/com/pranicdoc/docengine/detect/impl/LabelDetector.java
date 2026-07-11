package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.TextLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flags short text lines that read as field labels rather than filled-in values —
 * a colon-terminated line ("Patient Name:") scores highest; other short, digit-free
 * lines score lower. Real label/value disambiguation is layered on in Stage 6.
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
            String trimmed = line.text().trim();
            if (trimmed.isEmpty() || line.words().size() > MAX_LABEL_WORDS || trimmed.length() > MAX_LABEL_CHARS) {
                continue;
            }

            boolean endsWithColon = trimmed.endsWith(":");
            boolean digitFree = trimmed.chars().noneMatch(Character::isDigit);
            if (!endsWithColon && !digitFree) {
                continue;
            }

            double confidence = endsWithColon ? 0.99 : 0.6;
            candidates.add(new DetectionCandidate(
                ID, line.box(), CandidateType.LABEL, confidence, line.page(), Map.of("text", trimmed)
            ));
        }
        return candidates;
    }
}
