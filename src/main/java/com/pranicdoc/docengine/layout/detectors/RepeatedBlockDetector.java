package com.pranicdoc.docengine.layout.detectors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups the scope's ROW children by a coarse structural signature (text-line count,
 * vector-primitive count, rounded row height) and tags consecutive runs of matching rows
 * — e.g. every row in a scored checklist — with a shared repeatingGroupId attribute, mutating
 * the row nodes in place. Returns just the rows that ended up tagged.
 */
public class RepeatedBlockDetector implements LayoutDetector {

    @Override
    public List<LayoutNode> detect(LayoutNode scope, PipelineContext ctx) {
        List<LayoutNode> rows = scope.children().stream().filter(n -> n.type() == LayoutNodeType.ROW).toList();
        int minRun = ctx.config().repeatedBlockMinRunLength();

        List<LayoutNode> tagged = new ArrayList<>();
        int groupId = 0;
        int i = 0;
        while (i < rows.size()) {
            String signature = signature(rows.get(i));
            int j = i + 1;
            while (j < rows.size() && signature(rows.get(j)).equals(signature)) {
                j++;
            }
            if (j - i >= minRun) {
                String groupKey = scope.id() + "-repeat-" + groupId;
                for (int k = i; k < j; k++) {
                    rows.get(k).putAttribute("repeatingGroupId", groupKey);
                    tagged.add(rows.get(k));
                }
                groupId++;
            }
            i = j;
        }
        return tagged;
    }

    private String signature(LayoutNode row) {
        // Prefer the row's own pre-reattachment counts when DefaultLayoutAnalyzer recorded them:
        // a reattached spanning item (a rotated category label, a table-wide boundary line) can
        // overlap just 2-3 rows out of a much longer otherwise-identical run purely by geometric
        // coincidence, and counting it would make those rows look structurally different from
        // their neighbors for no real reason.
        Long ownLines = row.attribute("ownLineCount");
        Long ownVectors = row.attribute("ownVectorCount");
        int lineCount;
        int vectorCount;
        if (ownLines != null && ownVectors != null) {
            lineCount = ownLines.intValue();
            vectorCount = ownVectors.intValue();
        } else {
            List<TextLine> lines = row.attribute("textLines");
            List<VectorPrimitive> vectors = row.attribute("vectorPrimitives");
            lineCount = lines == null ? 0 : lines.size();
            vectorCount = vectors == null ? 0 : vectors.size();
        }
        // Grid-edge rows (first/last) naturally carry one fewer shared boundary line and a
        // slightly shorter reattached-content box than interior rows of the same repeating
        // structure — bucketing coarsely (instead of exact counts) tolerates that without
        // losing the ability to tell genuinely different row shapes apart.
        int vectorBucket = vectorCount / 5;
        int heightBucket = (int) Math.round(row.box().height() / 8.0);
        return lineCount + ":" + vectorBucket + ":" + heightBucket;
    }
}
