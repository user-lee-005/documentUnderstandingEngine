package com.pranicdoc.docengine.detect.impl;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.detect.FieldCandidateDetector;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.RectanglePrimitive;
import com.pranicdoc.docengine.primitives.model.TextChar;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native-PDF checkbox/option-box detection — no OpenCV required. Two sources:
 *
 * <p><b>Vector shapes</b>: option boxes arrive from the content stream in pieces (a circle is
 * 4 Bezier segments, a rounded rect is 4 corner arcs + 4 edge lines, a plain square a single
 * small RectanglePrimitive). Small primitives are clustered by proximity and any cluster with
 * a checkbox-sized union box is flagged. A tick mark drawn inside simply joins the cluster.
 *
 * <p><b>Text glyphs</b>: Word-exported forms draw checkboxes as characters (U+2610 BALLOT BOX
 * in MS-Gothic, etc.), invisible to the vector path. Those become candidates with an explicit
 * {@code checked} attribute derived from the glyph itself.
 *
 * <p>The OpenCV path for <i>scanned</i> pages remains Roadmap Phase 4.
 */
public class CheckboxDetector implements FieldCandidateDetector {

    /** u F06F / u F0A8 are Wingdings private-use empty boxes; F0FE / F0FD checked. */
    private static final Set<Character> UNCHECKED_GLYPHS = Set.of('\u2610', '\u25A1', '\u25A2', '\u2751', '\u2752', '\uF06F', '\uF0A8');
    private static final Set<Character> CHECKED_GLYPHS = Set.of('\u2611', '\u2612', '\u25A0', '\u25A3', '\uF0FE', '\uF0FD');

    public static final String ID = "checkbox-detector";

    /** Primitives larger than this can't be part of a checkbox shape. */
    private static final double MAX_MEMBER_EXTENT_PTS = 72.0;
    /** Two primitives closer than this (bbox gap) belong to the same shape. */
    private static final double CLUSTER_GAP_PTS = 2.5;

    /** Caption background strips are ~6.5pt tall; real option boxes start around 8pt. */
    private static final double MIN_BOX_WIDTH_PTS = 6.0;
    /** Wide labelled option boxes ("NON-VEGETARIAN") reach ~65pt. */
    private static final double MAX_BOX_WIDTH_PTS = 72.0;
    private static final double MIN_BOX_HEIGHT_PTS = 7.5;
    private static final double MAX_BOX_HEIGHT_PTS = 18.0;
    private static final double CONFIDENCE = 0.75;

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
        List<DetectionCandidate> candidates = new ArrayList<>();

        List<VectorPrimitive> primitives = scope.attribute("vectorPrimitives");
        if (primitives != null) {
            for (Cluster cluster : clusterSmallPrimitives(primitives)) {
                if (!isCheckboxShaped(cluster)) {
                    continue;
                }
                candidates.add(new DetectionCandidate(
                        ID, cluster.box, CandidateType.CHECKBOX, CONFIDENCE, cluster.page,
                        Map.of("members", cluster.members.size())));
            }
        }

        List<TextLine> lines = scope.attribute("textLines");
        if (lines != null) {
            for (TextLine line : lines) {
                for (TextWord word : line.words()) {
                    for (TextChar ch : word.chars()) {
                        char c = ch.value().isEmpty() ? 0 : ch.value().charAt(0);
                        boolean checked = CHECKED_GLYPHS.contains(c);
                        if (checked || UNCHECKED_GLYPHS.contains(c)) {
                            candidates.add(new DetectionCandidate(
                                    ID, ch.box(), CandidateType.CHECKBOX, CONFIDENCE, line.page(),
                                    Map.of("members", 1, "glyph", String.valueOf(c), "checked", checked)));
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private static List<Cluster> clusterSmallPrimitives(List<VectorPrimitive> primitives) {
        List<Cluster> clusters = new ArrayList<>();
        for (VectorPrimitive vp : primitives) {
            BoundingBox box = vp.box();
            if (box.width() > MAX_MEMBER_EXTENT_PTS || box.height() > MAX_MEMBER_EXTENT_PTS) {
                continue;
            }
            if (vp instanceof RectanglePrimitive rect && rect.filled()) {
                continue; // filled strips are caption backgrounds/whiteouts, never checkbox outlines
            }
            Cluster home = null;
            for (Cluster cluster : clusters) {
                if (cluster.page == vp.page() && gap(cluster.box, box) <= CLUSTER_GAP_PTS) {
                    if (home == null) {
                        cluster.add(vp, box);
                        home = cluster;
                    } else {
                        home.absorb(cluster);
                        cluster.members.clear();
                    }
                }
            }
            if (home == null) {
                clusters.add(new Cluster(vp, box));
            }
        }
        clusters.removeIf(c -> c.members.isEmpty());
        return clusters;
    }

    private static boolean isCheckboxShaped(Cluster cluster) {
        double w = cluster.box.width();
        double h = cluster.box.height();
        boolean sized = w >= MIN_BOX_WIDTH_PTS && w <= MAX_BOX_WIDTH_PTS
                && h >= MIN_BOX_HEIGHT_PTS && h <= MAX_BOX_HEIGHT_PTS;
        if (!sized) {
            return false;
        }
        // Closed shapes arrive in ≥3 pieces; a lone primitive only qualifies if it is itself
        // a drawn small rectangle (a plain square checkbox).
        return cluster.members.size() >= 3
                || cluster.members.stream().anyMatch(m -> m instanceof RectanglePrimitive);
    }

    /** Gap between two boxes; 0 when touching/overlapping. */
    private static double gap(BoundingBox a, BoundingBox b) {
        double dx = Math.max(0, Math.max(b.x0() - a.x1(), a.x0() - b.x1()));
        double dy = Math.max(0, Math.max(b.y0() - a.y1(), a.y0() - b.y1()));
        return Math.max(dx, dy);
    }

    private static final class Cluster {
        final List<VectorPrimitive> members = new ArrayList<>();
        BoundingBox box;
        final int page;

        Cluster(VectorPrimitive first, BoundingBox box) {
            this.members.add(first);
            this.box = box;
            this.page = first.page();
        }

        void add(VectorPrimitive vp, BoundingBox vpBox) {
            members.add(vp);
            box = union(box, vpBox);
        }

        void absorb(Cluster other) {
            members.addAll(other.members);
            box = union(box, other.box);
        }

        private static BoundingBox union(BoundingBox a, BoundingBox b) {
            return BoundingBox.of(Math.min(a.x0(), b.x0()), Math.min(a.y0(), b.y0()),
                    Math.max(a.x1(), b.x1()), Math.max(a.y1(), b.y1()));
        }
    }
}
