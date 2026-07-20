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

class CaptionBandDetectorTest {

    @Test
    void claimsTheBandBelowAFieldsetCaptionOutToTheSectionEdge() {
        // "EMAIL" caption on a divider line; value is written in the empty band below it
        TextLine captionRow = line(0, word("EMAIL", 54, 690, 6, 6.0));
        LayoutNode section = sectionWithRows(new BoundingBox(49, 610, 285, 746), rowOf(captionRow));

        List<DetectionCandidate> candidates = detect(section);

        assertEquals(1, candidates.size());
        DetectionCandidate band = candidates.get(0);
        assertEquals(CandidateType.WHITESPACE, band.type());
        assertEquals("EMAIL", band.attributes().get("caption"));
        assertEquals(54, band.box().x0(), 0.01);
        assertEquals(280, band.box().x1(), 0.01, "extends to section right edge minus margin");
        assertTrue(band.box().y1() < 690, "band sits strictly below the caption");
    }

    @Test
    void sideBySideCaptionsSplitTheRowIntoAdjacentBands() {
        // "AGE" and "GENDER" share a visual row: AGE's band must stop before GENDER starts
        TextLine captionRow = line(0,
                word("AGE", 54, 715, 6, 6.0),
                word("GENDER", 109, 715, 6, 6.0));
        LayoutNode section = sectionWithRows(new BoundingBox(49, 610, 285, 746), rowOf(captionRow));

        List<DetectionCandidate> candidates = detect(section);

        assertEquals(2, candidates.size());
        assertEquals(105, candidates.get(0).box().x1(), 0.01, "AGE band ends before GENDER caption");
        assertEquals(109, candidates.get(1).box().x0(), 0.01);
    }

    @Test
    void ignoresValueTextAndNoOpsOnLeafScopes() {
        TextLine valueRow = line(0, word("Coimbatore", 320, 696, 10, 10.0));
        LayoutNode section = sectionWithRows(new BoundingBox(310, 610, 545, 746), rowOf(valueRow));
        assertTrue(detect(section).isEmpty(), "mixed-case value text is not a caption");

        LayoutNode leaf = rowOf(line(0, word("EMAIL", 54, 690, 6, 6.0)));
        assertTrue(detect(leaf).isEmpty(), "leaf scopes have no section context — detector must no-op");
    }

    // ---- fixtures ----

    private static List<DetectionCandidate> detect(LayoutNode scope) {
        return new CaptionBandDetector().detect(scope, new PipelineContext(EngineConfig.defaults()));
    }

    private static LayoutNode sectionWithRows(BoundingBox box, LayoutNode... rows) {
        LayoutNode section = new LayoutNode("section-0", LayoutNodeType.SECTION, box, 0);
        for (LayoutNode row : rows) {
            section.addChild(row);
        }
        return section;
    }

    private static LayoutNode rowOf(TextLine line) {
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
