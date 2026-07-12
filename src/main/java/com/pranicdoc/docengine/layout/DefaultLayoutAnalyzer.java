package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.detectors.ColumnDetector;
import com.pranicdoc.docengine.layout.detectors.HeaderFooterDetector;
import com.pranicdoc.docengine.layout.detectors.HeaderFooterResult;
import com.pranicdoc.docengine.layout.detectors.RepeatedBlockDetector;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Builds the real Page -> Column -> Section -> Row tree. Block/Field/Value granularity is
 * still Stage 4's job (Roadmap Phase 2+) — Stage 4 detectors keep reading the page node's
 * flat textLines/vectorPrimitives attributes (set to the complete, unfiltered per-page lists,
 * exactly as in the Phase 0 scaffold) so nothing downstream needs to change yet. The nested
 * tree beneath the page node is built from header/footer-excluded body content and exists so
 * later phases (repeated-block-aware detection, the relationship graph, debug visualization)
 * have real structure to work from instead of a placeholder.
 */
public class DefaultLayoutAnalyzer implements LayoutAnalyzer {

    private final HeaderFooterDetector headerFooterDetector = new HeaderFooterDetector();
    private final ColumnDetector columnDetector = new ColumnDetector();
    private final RepeatedBlockDetector repeatedBlockDetector = new RepeatedBlockDetector();

    @Override
    public DocumentLayout analyzeDocument(List<PageContent> pages, PipelineContext ctx) {
        HeaderFooterResult headerFooter = headerFooterDetector.detect(pages, ctx);

        List<LayoutNode> pageRoots = new ArrayList<>();
        List<ReadingOrder> readingOrders = new ArrayList<>();
        for (PageContent page : pages) {
            LayoutNode pageNode = buildPageNode(page, headerFooter, ctx);
            pageRoots.add(pageNode);
            readingOrders.add(computeReadingOrder(pageNode));
        }
        return new DocumentLayout(pageRoots, readingOrders);
    }

    private LayoutNode buildPageNode(PageContent page, HeaderFooterResult headerFooter, PipelineContext ctx) {
        BoundingBox pageBox = new BoundingBox(0, 0, page.dimensions().widthPts(), page.dimensions().heightPts());
        LayoutNode pageNode = new LayoutNode("page-" + page.pageIndex(), LayoutNodeType.PAGE, pageBox, page.pageIndex());

        pageNode.putAttribute("textLines", page.textLines());
        pageNode.putAttribute("vectorPrimitives", page.vectorPrimitives());
        pageNode.putAttribute("images", page.images());

        LayoutNode headerNode = buildBand(page, headerFooter.headerLines(), "header");
        if (headerNode != null) {
            pageNode.addChild(headerNode);
        }

        List<TextLine> bodyLines = page.textLines().stream()
            .filter(l -> !headerFooter.headerLines().contains(l) && !headerFooter.footerLines().contains(l))
            .toList();

        LayoutNode bodyScope = new LayoutNode(pageNode.id() + "-body", LayoutNodeType.SECTION, pageBox, page.pageIndex());
        bodyScope.putAttribute("textLines", bodyLines);
        bodyScope.putAttribute("vectorPrimitives", page.vectorPrimitives());

        for (LayoutNode column : columnDetector.detect(bodyScope, ctx)) {
            List<LayoutNode> rows = buildRows(column, ctx.config());
            List<LayoutNode> sections = groupIntoSections(column, rows, ctx.config());
            for (LayoutNode section : sections) {
                repeatedBlockDetector.detect(section, ctx);
            }
            sections.forEach(column::addChild);
            pageNode.addChild(column);
        }

        LayoutNode footerNode = buildBand(page, headerFooter.footerLines(), "footer");
        if (footerNode != null) {
            pageNode.addChild(footerNode);
        }

        return pageNode;
    }

    private LayoutNode buildBand(PageContent page, Set<TextLine> lines, String role) {
        List<TextLine> pageLines = lines.stream().filter(l -> l.page() == page.pageIndex()).toList();
        if (pageLines.isEmpty()) {
            return null;
        }
        BoundingBox box = BoundingBox.unionOf(pageLines.stream().map(TextLine::box).toList());
        LayoutNode band = new LayoutNode("page-" + page.pageIndex() + "-" + role, LayoutNodeType.SECTION, box, page.pageIndex());
        band.putAttribute("role", role);
        band.putAttribute("textLines", pageLines);
        band.putAttribute("vectorPrimitives", List.<VectorPrimitive>of());
        return band;
    }

    /**
     * Items taller than this do not participate in row <i>formation</i> — a 700pt table rule or
     * a rotated section label would otherwise chain every text row it crosses into one giant
     * row. They are re-attached to every row they vertically overlap after clustering, so
     * detectors still see them.
     */
    private static final double MAX_ROW_FORMING_ITEM_HEIGHT_PTS = 30.0;

    /** Clusters a column's content into horizontal bands by Y-proximity — content drawn side by side lands in the same row. */
    private List<LayoutNode> buildRows(LayoutNode column, EngineConfig cfg) {
        List<TextLine> lines = column.attribute("textLines");
        List<VectorPrimitive> vectors = column.attribute("vectorPrimitives");
        lines = lines == null ? List.of() : lines;
        vectors = vectors == null ? List.of() : vectors;

        List<PositionedItem> all = new ArrayList<>();
        for (TextLine l : lines) {
            all.add(new PositionedItem(l.box(), l));
        }
        for (VectorPrimitive v : vectors) {
            all.add(new PositionedItem(v.box(), v));
        }

        List<PositionedItem> items = new ArrayList<>();
        List<PositionedItem> spanning = new ArrayList<>();
        for (PositionedItem item : all) {
            (item.box().height() <= MAX_ROW_FORMING_ITEM_HEIGHT_PTS ? items : spanning).add(item);
        }
        if (items.isEmpty()) {
            items = spanning; // page is nothing but tall content — fall back to clustering it directly
            spanning = List.of();
        }
        items = new ArrayList<>(items);
        items.sort((a, b) -> Double.compare(b.box().y1(), a.box().y1()));

        List<List<PositionedItem>> rowGroups = new ArrayList<>();
        List<PositionedItem> current = new ArrayList<>();
        double currentY0 = 0;
        for (PositionedItem item : items) {
            if (current.isEmpty()) {
                current.add(item);
                currentY0 = item.box().y0();
                continue;
            }
            if (item.box().y1() >= currentY0 - cfg.rowGroupingGapTolerancePts()) {
                current.add(item);
                currentY0 = Math.min(currentY0, item.box().y0());
            } else {
                rowGroups.add(current);
                current = new ArrayList<>(List.of(item));
                currentY0 = item.box().y0();
            }
        }
        if (!current.isEmpty()) {
            rowGroups.add(current);
        }

        // A cluster that is nothing but thin horizontal rules is a row BOUNDARY, not a row —
        // fold it into the row above. Without this, ruled tables alternate content-row /
        // rule-row and RepeatedBlockDetector can never see 3 consecutive identical rows.
        List<List<PositionedItem>> withBoundariesFolded = new ArrayList<>();
        for (List<PositionedItem> group : rowGroups) {
            boolean ruleOnly = group.stream().allMatch(
                it -> it.payload() instanceof VectorPrimitive && it.box().height() <= 2.0);
            if (ruleOnly && !withBoundariesFolded.isEmpty()) {
                withBoundariesFolded.get(withBoundariesFolded.size() - 1).addAll(group);
            } else {
                withBoundariesFolded.add(group);
            }
        }
        rowGroups = withBoundariesFolded;

        List<LayoutNode> rows = new ArrayList<>();
        int idx = 0;
        for (List<PositionedItem> group : rowGroups) {
            BoundingBox rowBox = BoundingBox.unionOf(group.stream().map(PositionedItem::box).toList());
            // spanning items (tall rules, containers, rotated labels) rejoin every row they overlap
            List<PositionedItem> members = new ArrayList<>(group);
            for (PositionedItem tall : spanning) {
                if (tall.box().y0() <= rowBox.y1() && tall.box().y1() >= rowBox.y0()) {
                    members.add(tall);
                }
            }
            List<TextLine> rowLines = members.stream().map(PositionedItem::payload).filter(p -> p instanceof TextLine).map(p -> (TextLine) p).toList();
            List<VectorPrimitive> rowVectors = members.stream().map(PositionedItem::payload).filter(p -> p instanceof VectorPrimitive).map(p -> (VectorPrimitive) p).toList();
            LayoutNode row = new LayoutNode(column.id() + "-row-" + idx, LayoutNodeType.ROW, rowBox, column.page());
            row.putAttribute("textLines", rowLines);
            row.putAttribute("vectorPrimitives", rowVectors);
            rows.add(row);
            idx++;
        }
        return rows;
    }

    /** Groups rows into sections wherever the gap to the next row is an outlier relative to the column's typical row spacing. */
    private List<LayoutNode> groupIntoSections(LayoutNode column, List<LayoutNode> rows, EngineConfig cfg) {
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            gaps.add(Math.max(0.0, rows.get(i - 1).box().y0() - rows.get(i).box().y1()));
        }
        double threshold = Math.max(cfg.minSectionBreakGapPts(), median(gaps) * cfg.sectionBreakGapMultiplier());

        List<List<LayoutNode>> sectionGroups = new ArrayList<>();
        List<LayoutNode> current = new ArrayList<>(List.of(rows.get(0)));
        for (int i = 1; i < rows.size(); i++) {
            double gap = rows.get(i - 1).box().y0() - rows.get(i).box().y1();
            if (gap > threshold) {
                sectionGroups.add(current);
                current = new ArrayList<>();
            }
            current.add(rows.get(i));
        }
        sectionGroups.add(current);

        List<LayoutNode> sections = new ArrayList<>();
        int idx = 0;
        for (List<LayoutNode> group : sectionGroups) {
            BoundingBox sectionBox = BoundingBox.unionOf(group.stream().map(LayoutNode::box).toList());
            LayoutNode section = new LayoutNode(column.id() + "-section-" + idx, LayoutNodeType.SECTION, sectionBox, column.page());
            group.forEach(section::addChild);
            sections.add(section);
            idx++;
        }
        return sections;
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }

    private ReadingOrder computeReadingOrder(LayoutNode pageNode) {
        List<String> order = new ArrayList<>();
        collectReadingOrder(pageNode, order);
        return new ReadingOrder(order);
    }

    private void collectReadingOrder(LayoutNode node, List<String> order) {
        order.add(node.id());
        for (LayoutNode child : node.children()) {
            collectReadingOrder(child, order);
        }
    }

    private record PositionedItem(BoundingBox box, Object payload) {
    }
}
