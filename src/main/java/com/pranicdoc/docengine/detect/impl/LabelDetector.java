package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.TextChar;
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
 * single long line. Detection therefore runs per <b>cluster</b>: a line's words are split
 * wherever the horizontal gap is too wide to be intra-caption spacing, and each cluster is
 * scored independently.
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

    /** Fieldset captions can be wordy: "IF YES TO ANY LOCATION ABOVE, PLEASE EXPLAIN MORE" is 9 words. */
    private static final int MAX_CAPTION_WORDS = 10;
    private static final int MAX_CAPTION_CHARS = 60;
    /** Caption fonts are small (CL01 uses 6pt); body/value text is 9pt+. */
    private static final double MAX_CAPTION_FONT_PT = 8.0;
    private static final double MIN_UPPERCASE_RATIO = 0.9;

    /** A gap wider than this multiple of the font size splits a line into separate clusters. */
    private static final double CLUSTER_GAP_FONT_MULTIPLE = 1.5;
    private static final double MIN_CLUSTER_GAP_PTS = 8.0;

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
            for (List<TextWord> cluster : clusters(line)) {
                scoreCluster(cluster, line.page(), candidates);
            }
        }
        return candidates;
    }

    /** Splits a visual row into word clusters wherever the x-gap exceeds intra-caption spacing. */
    private static List<List<TextWord>> clusters(TextLine line) {
        List<List<TextWord>> result = new ArrayList<>();
        List<TextWord> current = new ArrayList<>();
        TextWord previous = null;
        for (TextWord word : line.words()) {
            if (previous != null) {
                double gap = word.box().x0() - previous.box().x1();
                double threshold = Math.max(MIN_CLUSTER_GAP_PTS, CLUSTER_GAP_FONT_MULTIPLE * fontSizeOf(previous));
                if (gap > threshold) {
                    result.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(word);
            previous = word;
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    private static void scoreCluster(List<TextWord> cluster, int page, List<DetectionCandidate> out) {
        String text = String.join(" ", cluster.stream().map(TextWord::text).toList()).trim();
        if (text.isEmpty()) {
            return;
        }

        boolean endsWithColon = text.endsWith(":");
        boolean shortRun = cluster.size() <= MAX_LABEL_WORDS && text.length() <= MAX_LABEL_CHARS;
        boolean digitFree = text.chars().noneMatch(Character::isDigit);

        double confidence;
        if (endsWithColon && shortRun) {
            confidence = 0.99;
        } else if (isFieldsetCaption(cluster, text)) {
            confidence = 0.85;
        } else if (shortRun && digitFree) {
            confidence = 0.6;
        } else {
            return;
        }

        BoundingBox box = BoundingBox.unionOf(cluster.stream().map(TextWord::box).toList());
        out.add(new DetectionCandidate(ID, box, CandidateType.LABEL, confidence, page, Map.of("text", text)));
    }

    /** Short ALL-CAPS run in a small font — the caption style embedded in container borders. */
    private static boolean isFieldsetCaption(List<TextWord> cluster, String text) {
        if (cluster.size() > MAX_CAPTION_WORDS || text.length() > MAX_CAPTION_CHARS) {
            return false;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        if (letters < 2) {
            return false;
        }
        long uppercase = text.chars().filter(Character::isUpperCase).count();
        if (uppercase < letters * MIN_UPPERCASE_RATIO) {
            return false;
        }
        double avgFontSize = cluster.stream().flatMap(w -> w.chars().stream())
                .mapToDouble(TextChar::fontSize).average().orElse(Double.MAX_VALUE);
        return avgFontSize <= MAX_CAPTION_FONT_PT;
    }

    private static double fontSizeOf(TextWord word) {
        return word.chars().isEmpty() ? 10.0 : word.chars().get(word.chars().size() - 1).fontSize();
    }
}
