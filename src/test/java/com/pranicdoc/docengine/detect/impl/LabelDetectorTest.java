package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextChar;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelDetectorTest {

    @Test
    void colonTerminatedLineScoresHighest() {
        LayoutNode row = rowWith(line(0, word("Name:", 50, 700, 6, 10.0)));

        List<DetectionCandidate> candidates = detect(row);

        assertEquals(1, candidates.size());
        assertEquals(CandidateType.LABEL, candidates.get(0).type());
        assertEquals(0.99, candidates.get(0).rawConfidence(), 0.001);
    }

    @Test
    void smallFontAllCapsCaptionIsAFieldsetLabelEvenWithDigits() {
        // "ZIP / PINCODE"-style caption: 6pt, uppercase, no colon; digits must not disqualify
        LayoutNode row = rowWith(line(0,
                word("ADDRESS", 310.0, 740, 6, 6.0),
                word("LINE", 336.4, 740, 6, 6.0),
                word("1", 352.9, 740, 6, 6.0),
                word("&", 359.5, 740, 6, 6.0),
                word("2", 366.1, 740, 6, 6.0)));

        List<DetectionCandidate> candidates = detect(row);

        assertEquals(1, candidates.size());
        assertEquals(0.85, candidates.get(0).rawConfidence(), 0.001);
        assertEquals("ADDRESS LINE 1 & 2", candidates.get(0).attributes().get("text"));
    }

    @Test
    void sideBySideCaptionsOnOneVisualRowBecomeSeparateCandidates() {
        // PDFBox glues same-row captions into one TextLine; the wide gap must split them
        LayoutNode row = rowWith(line(0,
                word("PATIENT'S", 54.0, 740, 6, 6.0),
                word("NAME", 87.0, 740, 6, 6.0),
                // ~200pt gap to the right-hand column's caption
                word("ADDRESS", 314.0, 740, 6, 6.0),
                word("LINE", 340.4, 740, 6, 6.0)));

        List<DetectionCandidate> candidates = detect(row);

        assertEquals(2, candidates.size());
        assertEquals("PATIENT'S NAME", candidates.get(0).attributes().get("text"));
        assertEquals("ADDRESS LINE", candidates.get(1).attributes().get("text"));
        assertTrue(candidates.get(0).box().x1() < candidates.get(1).box().x0());
    }

    @Test
    void uppercaseValueInNormalFontSizeIsNotAFieldsetCaption() {
        // "WEEKLY" is all-caps but written at value size (10pt) — must not score 0.85
        LayoutNode row = rowWith(line(0, word("WEEKLY", 220, 600, 9, 10.0)));

        List<DetectionCandidate> candidates = detect(row);

        assertEquals(1, candidates.size());
        assertEquals(0.6, candidates.get(0).rawConfidence(), 0.001, "legacy short-run fallback, not caption");
    }

    @Test
    void longMixedCaseSentenceIsIgnored() {
        LayoutNode row = rowWith(line(0,
                word("Please", 56, 395, 8, 8.0),
                word("state", 84, 395, 8, 8.0),
                word("the", 105, 395, 8, 8.0),
                word("main", 120, 395, 8, 8.0),
                word("ailment", 141, 395, 8, 8.0),
                word("that", 170, 395, 8, 8.0),
                word("will", 187, 395, 8, 8.0),
                word("be", 202, 395, 8, 8.0),
                word("treated", 213, 395, 8, 8.0)));

        List<DetectionCandidate> candidates = detect(row);

        assertTrue(candidates.isEmpty());
    }

    // ---- fixtures ----

    private static List<DetectionCandidate> detect(LayoutNode row) {
        return new LabelDetector().detect(row, new PipelineContext(EngineConfig.defaults()));
    }

    private static LayoutNode rowWith(TextLine line) {
        LayoutNode row = new LayoutNode("row-0", LayoutNodeType.ROW, line.box(), line.page());
        row.putAttribute("textLines", List.of(line));
        return row;
    }

    private static TextLine line(int page, TextWord... words) {
        List<TextWord> list = List.of(words);
        BoundingBox box = BoundingBox.unionOf(list.stream().map(TextWord::box).toList());
        String text = String.join(" ", list.stream().map(TextWord::text).toList());
        return new TextLine(text, box, page, list);
    }

    private static TextWord word(String text, double x0, double y0, double height, double fontSize) {
        double charWidth = fontSize * 0.55;
        List<TextChar> chars = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            BoundingBox box = new BoundingBox(x0 + i * charWidth, y0, x0 + (i + 1) * charWidth, y0 + height);
            chars.add(new TextChar(String.valueOf(text.charAt(i)), box, "TestFont", fontSize));
        }
        BoundingBox box = new BoundingBox(x0, y0, x0 + text.length() * charWidth, y0 + height);
        return new TextWord(text, box, chars);
    }
}
