package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.PageContent;
import com.pranicdoc.docengine.primitives.model.TextLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Document-level, not page-level: a line only counts as a header/footer if its normalized
 * text repeats across a large enough fraction of pages' top/bottom bands. This can't work
 * one page at a time, so it doesn't implement the page-level LayoutDetector interface.
 */
public class HeaderFooterDetector {

    public HeaderFooterResult detect(List<PageContent> pages, PipelineContext ctx) {
        if (pages.size() < 2) {
            return new HeaderFooterResult(Set.of(), Set.of());
        }

        double bandFraction = ctx.config().headerFooterBandFraction();
        Map<String, Integer> headerCounts = new HashMap<>();
        Map<String, List<TextLine>> headerLinesByKey = new HashMap<>();
        Map<String, Integer> footerCounts = new HashMap<>();
        Map<String, List<TextLine>> footerLinesByKey = new HashMap<>();

        for (PageContent page : pages) {
            double pageHeight = page.dimensions().heightPts();
            double headerCutoff = pageHeight * (1 - bandFraction);
            double footerCutoff = pageHeight * bandFraction;

            for (TextLine line : page.textLines()) {
                String key = normalize(line.text());
                if (key.isEmpty()) {
                    continue;
                }
                if (line.box().y0() >= headerCutoff) {
                    headerCounts.merge(key, 1, Integer::sum);
                    headerLinesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
                } else if (line.box().y1() <= footerCutoff) {
                    footerCounts.merge(key, 1, Integer::sum);
                    footerLinesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
                }
            }
        }

        int minRepeats = (int) Math.ceil(pages.size() * ctx.config().headerFooterMinRepeatFraction());
        Set<TextLine> headerLines = collectRepeated(headerCounts, headerLinesByKey, minRepeats);
        Set<TextLine> footerLines = collectRepeated(footerCounts, footerLinesByKey, minRepeats);
        return new HeaderFooterResult(headerLines, footerLines);
    }

    private Set<TextLine> collectRepeated(Map<String, Integer> counts, Map<String, List<TextLine>> linesByKey, int minRepeats) {
        Set<TextLine> result = new HashSet<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= minRepeats) {
                result.addAll(linesByKey.get(entry.getKey()));
            }
        }
        return result;
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
