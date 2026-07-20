package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects multi-column layout via vertical whitespace gutters: bins the scope's content by
 * x-position, finds contiguous uncovered bands wide enough to be a real gutter (not just
 * inter-word spacing), and splits on those. A scope with no gutter, or no content at all,
 * still comes back as exactly one properly-typed COLUMN node — callers never need to special-case
 * "no columns found".
 */
public class ColumnDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode scope, PipelineContext ctx) {
        List<TextLine> textLines = attributeOrEmpty(scope, "textLines");
        List<VectorPrimitive> vectorPrimitives = attributeOrEmpty(scope, "vectorPrimitives");
        EngineConfig cfg = ctx.config();

        if (textLines.isEmpty() && vectorPrimitives.isEmpty()) {
            return List.of(asColumn(scope, 0, textLines, vectorPrimitives, scope.box()));
        }

        double pageWidth = scope.box().x1();
        double binWidth = cfg.columnGutterBinWidthPts();
        int binCount = (int) Math.ceil(pageWidth / binWidth) + 1;
        boolean[] covered = new boolean[binCount];
        for (TextLine line : textLines) {
            markCovered(covered, line.box(), binWidth);
        }
        for (VectorPrimitive vp : vectorPrimitives) {
            markCovered(covered, vp.box(), binWidth);
        }

        List<double[]> gaps = findGaps(covered, binWidth, cfg.columnGutterMinWidthPts());
        if (gaps.isEmpty()) {
            return List.of(asColumn(scope, 0, textLines, vectorPrimitives, scope.box()));
        }

        List<double[]> ranges = splitByGaps(pageWidth, gaps);
        List<LayoutNode> columns = new ArrayList<>();
        int idx = 0;
        for (double[] range : ranges) {
            double x0 = range[0];
            double x1 = range[1];
            List<TextLine> colLines = textLines.stream().filter(l -> withinX(l.box(), x0, x1)).toList();
            List<VectorPrimitive> colVectors = vectorPrimitives.stream().filter(v -> withinX(v.box(), x0, x1)).toList();
            if (colLines.isEmpty() && colVectors.isEmpty()) {
                continue;
            }
            BoundingBox columnBox = new BoundingBox(x0, scope.box().y0(), x1, scope.box().y1());
            columns.add(asColumn(scope, idx, colLines, colVectors, columnBox));
            idx++;
        }
        return columns.isEmpty() ? List.of(asColumn(scope, 0, textLines, vectorPrimitives, scope.box())) : columns;
    }

    private LayoutNode asColumn(LayoutNode scope, int idx, List<TextLine> lines, List<VectorPrimitive> vectors, BoundingBox box) {
        LayoutNode column = new LayoutNode(scope.id() + "-col-" + idx, LayoutNodeType.COLUMN, box, scope.page());
        column.putAttribute("textLines", lines);
        column.putAttribute("vectorPrimitives", vectors);
        return column;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> attributeOrEmpty(LayoutNode node, String key) {
        List<T> value = node.attribute(key);
        return value == null ? List.of() : value;
    }

    private boolean withinX(BoundingBox box, double x0, double x1) {
        double center = box.centerX();
        return center >= x0 && center < x1;
    }

    private void markCovered(boolean[] covered, BoundingBox box, double binWidth) {
        int startBin = Math.max(0, (int) Math.floor(box.x0() / binWidth));
        int endBin = Math.min(covered.length, (int) Math.ceil(box.x1() / binWidth));
        for (int i = startBin; i < endBin; i++) {
            covered[i] = true;
        }
    }

    private List<double[]> findGaps(boolean[] covered, double binWidth, double minGapWidth) {
        List<double[]> gaps = new ArrayList<>();
        int i = 0;
        while (i < covered.length) {
            if (covered[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < covered.length && !covered[i]) {
                i++;
            }
            double gapWidth = (i - start) * binWidth;
            if (gapWidth >= minGapWidth) {
                gaps.add(new double[]{start * binWidth, i * binWidth});
            }
        }
        return gaps;
    }

    private List<double[]> splitByGaps(double pageWidth, List<double[]> gaps) {
        List<double[]> ranges = new ArrayList<>();
        double cursor = 0;
        for (double[] gap : gaps) {
            ranges.add(new double[]{cursor, gap[0]});
            cursor = gap[1];
        }
        ranges.add(new double[]{cursor, pageWidth});
        return ranges;
    }
}
