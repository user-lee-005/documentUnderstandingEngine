package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.primitives.model.TextChar;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared caption geometry/typography heuristics, used by {@link LabelDetector} (to flag
 * captions as LABEL candidates) and {@link CaptionBandDetector} (to claim the writable band
 * beneath them). Kept in one place so the two detectors can never disagree about what a
 * caption is.
 */
final class CaptionHeuristics {

    /** Fieldset captions can be wordy: "IF YES TO ANY LOCATION ABOVE, PLEASE EXPLAIN MORE" is 9 words. */
    static final int MAX_CAPTION_WORDS = 10;
    static final int MAX_CAPTION_CHARS = 60;
    /** Caption fonts are small (CL01 uses 6pt); body/value text is 9pt+. */
    static final double MAX_CAPTION_FONT_PT = 8.0;
    static final double MIN_UPPERCASE_RATIO = 0.9;

    /** A gap wider than this multiple of the font size splits a line into separate clusters. */
    private static final double CLUSTER_GAP_FONT_MULTIPLE = 1.5;
    private static final double MIN_CLUSTER_GAP_PTS = 8.0;

    private CaptionHeuristics() {
    }

    /** Splits a visual row into word clusters wherever the x-gap exceeds intra-caption spacing. */
    static List<List<TextWord>> clusters(TextLine line) {
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

    static String textOf(List<TextWord> cluster) {
        return String.join(" ", cluster.stream().map(TextWord::text).toList()).trim();
    }

    /** Short ALL-CAPS run in a small font — the caption style embedded in container borders. */
    static boolean isFieldsetCaption(List<TextWord> cluster, String text) {
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

    static double fontSizeOf(TextWord word) {
        return word.chars().isEmpty() ? 10.0 : word.chars().get(word.chars().size() - 1).fontSize();
    }
}
