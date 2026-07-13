package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.layout.LayoutNodeType;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fine-grained cell detection within the row groups Phase 1's RepeatedBlockDetector already
 * tagged as repeating (repeatingGroupId) — a Camelot-"stream"-style approach: cluster item
 * x-centers across those rows into table columns by proximity, rather than requiring ruled
 * cell borders. Runs against a SECTION scope whose children are ROWs; no-ops everywhere else.
 */
public class TableDetector implements FieldCandidateDetector {

    public static final String ID = "table-detector";
    private static final double COLUMN_CLUSTER_GAP_PTS = 8.0;

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
        List<LayoutNode> rows = scope.children().stream().filter(n -> n.type() == LayoutNodeType.ROW).toList();

        Map<String, List<LayoutNode>> groups = new LinkedHashMap<>();
        for (LayoutNode row : rows) {
            String groupId = row.attribute("repeatingGroupId");
            if (groupId != null) {
                groups.computeIfAbsent(groupId, k -> new ArrayList<>()).add(row);
            }
        }

        List<DetectionCandidate> candidates = new ArrayList<>();
        for (List<LayoutNode> group : groups.values()) {
            candidates.addAll(detectTableCells(group));
        }
        return candidates;
    }

    /**
     * DefaultLayoutAnalyzer re-attaches tall content (vlines, section-background fills) to
     * every row it vertically overlaps, so a single physical primitive can show up identically
     * in many rows. Real per-row cell content is roughly one row tall; anything much taller is
     * that kind of re-attached artifact, not a cell — including it would fabricate an identical
     * bogus "column" in every row and (via NMS) crowd out the real per-cell candidates.
     */
    private static final double MAX_CELL_HEIGHT_TO_ROW_HEIGHT_RATIO = 1.5;

    /** A column present in fewer than this fraction of the group's rows is an outlier, not this table's own structure. */
    private static final double MIN_COLUMN_CONSISTENCY_FOR_AGGREGATE = 0.3;

    private List<DetectionCandidate> detectTableCells(List<LayoutNode> rows) {
        List<Item> items = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            LayoutNode row = rows.get(r);
            double maxCellHeight = row.box().height() * MAX_CELL_HEIGHT_TO_ROW_HEIGHT_RATIO;
            for (TextLine line : this.<TextLine>attrOrEmpty(row, "textLines")) {
                items.add(new Item(line.box(), r, row.page()));
            }
            for (VectorPrimitive vp : this.<VectorPrimitive>attrOrEmpty(row, "vectorPrimitives")) {
                if (vp.box().height() > maxCellHeight) {
                    continue;
                }
                items.add(new Item(vp.box(), r, row.page()));
            }
        }
        if (items.isEmpty()) {
            return List.of();
        }

        List<Item> byX = items.stream().sorted(Comparator.comparingDouble(it -> it.box().centerX())).toList();
        List<List<Item>> columns = new ArrayList<>();
        List<Item> currentColumn = new ArrayList<>();
        double lastX = Double.NEGATIVE_INFINITY;
        for (Item item : byX) {
            if (!currentColumn.isEmpty() && item.box().centerX() - lastX > COLUMN_CLUSTER_GAP_PTS) {
                columns.add(currentColumn);
                currentColumn = new ArrayList<>();
            }
            currentColumn.add(item);
            lastX = item.box().centerX();
        }
        columns.add(currentColumn);

        List<DetectionCandidate> candidates = new ArrayList<>();
        List<BoundingBox> aggregateBoxes = new ArrayList<>();
        for (int col = 0; col < columns.size(); col++) {
            List<Item> column = columns.get(col);
            long distinctRows = column.stream().map(Item::rowIndex).distinct().count();
            double consistency = (double) distinctRows / rows.size();
            double confidence = 0.5 + 0.4 * consistency;
            for (Item item : column) {
                Map<String, Object> attrs = Map.of("row", item.rowIndex(), "col", col);
                candidates.add(new DetectionCandidate(ID, item.box(), CandidateType.TABLE, confidence, item.page(), attrs));
            }
            // A column that repeats across most rows is this table's own structure (a genuine
            // data column, or a divider every row draws). A column that shows up in only a
            // couple of rows is an outlier — typically a multi-row-spanning merged cell's own
            // edge (re-attached to just the rows it happens to overlap) or the table's single
            // outer boundary line — and must not stretch the aggregate box to include it.
            if (consistency >= MIN_COLUMN_CONSISTENCY_FOR_AGGREGATE) {
                aggregateBoxes.addAll(column.stream().map(Item::box).toList());
            }
        }

        // One aggregate candidate spanning the whole repeating group — "this region is a table".
        // Per-cell candidates locate content; downstream consumers (pairing, template learning,
        // eval) also need the table as a single region.
        if (aggregateBoxes.isEmpty()) {
            aggregateBoxes = items.stream().map(Item::box).toList();
        }
        BoundingBox tableBox = BoundingBox.unionOf(aggregateBoxes);
        candidates.add(new DetectionCandidate(ID, tableBox, CandidateType.TABLE, 0.75, items.get(0).page(),
                Map.of("aggregate", true, "rows", rows.size(), "cols", columns.size())));
        return candidates;
    }

    private <T> List<T> attrOrEmpty(LayoutNode node, String key) {
        List<T> value = node.attribute(key);
        return value == null ? List.of() : value;
    }

    private record Item(BoundingBox box, int rowIndex, int page) {
    }
}
