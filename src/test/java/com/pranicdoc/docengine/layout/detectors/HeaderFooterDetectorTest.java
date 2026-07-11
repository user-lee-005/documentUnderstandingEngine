package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.classify.PageDimensions;
import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.PageContent;
import com.pranicdoc.docengine.primitives.model.TextLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderFooterDetectorTest {

    @Test
    void detectsRepeatedTopAndBottomLinesAcrossPages() {
        PageDimensions dims = new PageDimensions(612, 800, 0);
        List<PageContent> pages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TextLine header = new TextLine("Confidential", new BoundingBox(50, 780, 150, 792), i, List.of());
            TextLine footer = new TextLine("Page Footer", new BoundingBox(50, 50, 150, 60), i, List.of());
            TextLine body = new TextLine("Body text " + i, new BoundingBox(50, 400, 150, 412), i, List.of());
            pages.add(new PageContent(i, dims, List.of(header, footer, body), List.of(), List.of()));
        }

        HeaderFooterResult result = new HeaderFooterDetector().detect(pages, new PipelineContext(EngineConfig.defaults()));

        assertEquals(3, result.headerLines().size());
        assertEquals(3, result.footerLines().size());
        assertTrue(result.headerLines().stream().allMatch(l -> l.text().equals("Confidential")));
        assertTrue(result.footerLines().stream().allMatch(l -> l.text().equals("Page Footer")));
        assertFalse(result.headerLines().stream().anyMatch(l -> l.text().startsWith("Body")));
    }

    @Test
    void returnsEmptyResultForASinglePageDocument() {
        PageDimensions dims = new PageDimensions(612, 800, 0);
        TextLine header = new TextLine("Confidential", new BoundingBox(50, 780, 150, 792), 0, List.of());
        List<PageContent> pages = List.of(new PageContent(0, dims, List.of(header), List.of(), List.of()));

        HeaderFooterResult result = new HeaderFooterDetector().detect(pages, new PipelineContext(EngineConfig.defaults()));

        assertTrue(result.headerLines().isEmpty());
        assertTrue(result.footerLines().isEmpty());
    }
}
