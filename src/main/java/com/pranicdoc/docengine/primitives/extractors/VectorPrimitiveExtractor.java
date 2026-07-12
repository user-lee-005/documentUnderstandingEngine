package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.primitives.model.CurvePrimitive;
import com.pranicdoc.docengine.primitives.model.LinePrimitive;
import com.pranicdoc.docengine.primitives.model.RectanglePrimitive;
import com.pranicdoc.docengine.primitives.model.VectorPrimitive;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generalizes pdfWorker's FormBoxDetector pattern (a PDFGraphicsStreamEngine subclass) but only
 * extracts primitives — it makes no judgment about which rectangles/lines are form fields.
 * That judgment is deferred entirely to Stage 4 detectors (RectangleDetector, UnderlineDetector, ...).
 */
public class VectorPrimitiveExtractor implements PrimitiveExtractor<VectorPrimitive> {

    @Override
    public List<VectorPrimitive> extract(PDDocument document, int pageIndex, PipelineContext ctx) throws IOException {
        PDPage page = document.getPage(pageIndex);
        PathWalker walker = new PathWalker(page, pageIndex);
        walker.processPage(page);

        List<VectorPrimitive> result = new ArrayList<>();
        result.addAll(walker.rectangles);
        result.addAll(walker.lines);
        result.addAll(walker.curves);
        return result;
    }

    private static final class PathWalker extends PDFGraphicsStreamEngine {

        private final int pageIndex;
        private final List<RectanglePrimitive> rectangles = new ArrayList<>();
        private final List<LinePrimitive> lines = new ArrayList<>();
        private final List<CurvePrimitive> curves = new ArrayList<>();
        private double[] pendingRect;
        private float moveX;
        private float moveY;

        PathWalker(PDPage page, int pageIndex) throws IOException {
            super(page);
            this.pageIndex = pageIndex;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            pendingRect = new double[]{
                Math.min(p0.getX(), p2.getX()),
                Math.min(p0.getY(), p2.getY()),
                Math.max(p0.getX(), p2.getX()),
                Math.max(p0.getY(), p2.getY())
            };
        }

        @Override
        public void strokePath() {
            commitPendingRect(false);
        }

        @Override
        public void fillPath(int windingRule) {
            commitPendingRect(true);
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            // must delegate so fill+stroke in one operator is still captured, same as FormBoxDetector
            commitPendingRect(true);
        }

        private void commitPendingRect(boolean filled) {
            if (pendingRect == null) {
                return;
            }
            BoundingBox box = new BoundingBox(pendingRect[0], pendingRect[1], pendingRect[2], pendingRect[3]);
            rectangles.add(new RectanglePrimitive(box, pageIndex, filled, 1.0));
            pendingRect = null;
        }

        @Override
        public void moveTo(float x, float y) {
            moveX = x;
            moveY = y;
        }

        @Override
        public void lineTo(float x, float y) {
            lines.add(new LinePrimitive(moveX, moveY, x, y, pageIndex, 1.0));
            moveX = x;
            moveY = y;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            curves.add(new CurvePrimitive(moveX, moveY, x1, y1, x2, y2, x3, y3, pageIndex));
            moveX = x3;
            moveY = y3;
        }

        @Override
        public void closePath() {
            // no-op — the closing segment (if any) was already emitted by the preceding lineTo
        }

        @Override
        public void endPath() {
            pendingRect = null;
        }

        @Override
        public void clip(int windingRule) {
            // no-op — clipping paths are not drawn primitives
        }

        @Override
        public void drawImage(PDImage pdImage) {
            // no-op — image extraction is ImagePrimitiveExtractor's responsibility
        }

        @Override
        public void shadingFill(COSName shadingName) {
            // no-op — not a candidate primitive
        }

        @Override
        public Point2D.Float getCurrentPoint() {
            return new Point2D.Float(moveX, moveY);
        }

        // transformedPoint() is deliberately NOT overridden: the base implementation applies the
        // CTM, and every path operator routes its coordinates through it. An identity override
        // here once stripped the transform from all paths drawn in a translated coordinate
        // system — every such line collapsed to origin-relative coordinates and was then
        // NMS-merged into a single bogus candidate.
    }
}
