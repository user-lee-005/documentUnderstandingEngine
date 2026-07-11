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
        List<TextLine> lines = row.attribute("textLines");
        List<VectorPrimitive> vectors = row.attribute("vectorPrimitives");
        int lineCount = lines == null ? 0 : lines.size();
        int vectorCount = vectors == null ? 0 : vectors.size();
        int heightBucket = (int) Math.round(row.box().height() / 2.0);
        return lineCount + ":" + vectorCount + ":" + heightBucket;
    }
}
