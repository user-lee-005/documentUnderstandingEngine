package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.primitives.model.ImagePrimitive;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedded raster image (logo/photo/full-page scan body) extraction. An image XObject is drawn
 * as a unit square transformed by the current transformation matrix at the moment of the `Do`
 * operator — the placed bounding box comes from transforming that square's 4 corners, not from
 * the XObject's own pixel dimensions.
 */
public class ImagePrimitiveExtractor implements PrimitiveExtractor<ImagePrimitive> {

    @Override
    public List<ImagePrimitive> extract(PDDocument document, int pageIndex, PipelineContext ctx) throws IOException {
        PDPage page = document.getPage(pageIndex);
        ImageWalker walker = new ImageWalker(page, pageIndex);
        walker.processPage(page);
        return walker.images;
    }

    private static final class ImageWalker extends PDFGraphicsStreamEngine {

        private final int pageIndex;
        private final List<ImagePrimitive> images = new ArrayList<>();
        private float moveX;
        private float moveY;

        ImageWalker(PDPage page, int pageIndex) throws IOException {
            super(page);
            this.pageIndex = pageIndex;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Point2D.Float p00 = ctm.transformPoint(0, 0);
            Point2D.Float p10 = ctm.transformPoint(1, 0);
            Point2D.Float p01 = ctm.transformPoint(0, 1);
            Point2D.Float p11 = ctm.transformPoint(1, 1);

            double minX = Math.min(Math.min(p00.x, p10.x), Math.min(p01.x, p11.x));
            double maxX = Math.max(Math.max(p00.x, p10.x), Math.max(p01.x, p11.x));
            double minY = Math.min(Math.min(p00.y, p10.y), Math.min(p01.y, p11.y));
            double maxY = Math.max(Math.max(p00.y, p10.y), Math.max(p01.y, p11.y));

            BoundingBox box = new BoundingBox(minX, minY, maxX, maxY);
            String format = pdImage.getSuffix() != null ? pdImage.getSuffix() : "unknown";
            images.add(new ImagePrimitive(box, pageIndex, pdImage.getWidth(), pdImage.getHeight(), format));
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            // no-op — vector shapes are VectorPrimitiveExtractor's responsibility
        }

        @Override
        public void strokePath() {
            // no-op
        }

        @Override
        public void fillPath(int windingRule) {
            // no-op
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            // no-op
        }

        @Override
        public void moveTo(float x, float y) {
            moveX = x;
            moveY = y;
        }

        @Override
        public void lineTo(float x, float y) {
            moveX = x;
            moveY = y;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            moveX = x3;
            moveY = y3;
        }

        @Override
        public void closePath() {
            // no-op
        }

        @Override
        public void endPath() {
            // no-op
        }

        @Override
        public void clip(int windingRule) {
            // no-op
        }

        @Override
        public void shadingFill(COSName shadingName) {
            // no-op
        }

        @Override
        public Point2D.Float getCurrentPoint() {
            return new Point2D.Float(moveX, moveY);
        }

        @Override
        public Point2D.Float transformedPoint(float x, float y) {
            return new Point2D.Float(x, y);
        }
    }
}
