package com.pranicdoc.docengine.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.output.DocumentResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Draws every merged candidate's box onto a throwaway in-memory copy of the source PDF —
 * never mutates the PDDocument the caller passed in. The annotated PDF is rasterized to PNG
 * per page afterward, so the same boxes/captions show up there too without a second,
 * pixel-coordinate drawing pass — one source of truth for the annotation, not two.
 */
public class PdfDebugOverlayRenderer implements DebugOverlayRenderer {

    @Override
    public DebugExport render(PDDocument document, List<DetectionCandidate> candidates, DocumentResult result, OverlayStyle style) throws IOException {
        byte[] sourceBytes = toBytes(document);

        try (PDDocument workingCopy = Loader.loadPDF(sourceBytes)) {
            Map<Integer, List<DetectionCandidate>> byPage = candidates.stream()
                .collect(Collectors.groupingBy(DetectionCandidate::page));

            for (Map.Entry<Integer, List<DetectionCandidate>> entry : byPage.entrySet()) {
                int pageIndex = entry.getKey();
                if (pageIndex < 0 || pageIndex >= workingCopy.getNumberOfPages()) {
                    continue;
                }
                annotatePage(workingCopy, workingCopy.getPage(pageIndex), entry.getValue(), style);
            }

            byte[] annotatedPdf = toBytes(workingCopy);
            List<byte[]> pngPages = renderPngPages(workingCopy, style);
            String verboseJson = toVerboseJson(candidates, result);

            return new DebugExport(annotatedPdf, pngPages, verboseJson);
        }
    }

    private void annotatePage(PDDocument document, PDPage page, List<DetectionCandidate> pageCandidates, OverlayStyle style) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            for (DetectionCandidate candidate : pageCandidates) {
                Color color = ConfidenceColorScale.colorFor(candidate.rawConfidence());
                BoundingBox box = candidate.box();

                cs.setStrokingColor(color);
                cs.setLineWidth(style.strokeWidth());
                cs.addRect((float) box.x0(), (float) box.y0(), (float) box.width(), (float) box.height());
                cs.stroke();

                if (style.drawCaptions()) {
                    cs.setNonStrokingColor(color);
                    cs.beginText();
                    cs.setFont(font, 6);
                    cs.newLineAtOffset((float) box.x0(), (float) box.y1() + 2);
                    cs.showText(caption(candidate));
                    cs.endText();
                }
            }
        }
    }

    private String caption(DetectionCandidate candidate) {
        return String.format("%s %s %.2f", candidate.detectorId(), candidate.type(), candidate.rawConfidence());
    }

    private List<byte[]> renderPngPages(PDDocument document, OverlayStyle style) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        List<byte[]> pages = new ArrayList<>();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, style.renderDpi());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            pages.add(out.toByteArray());
        }
        return pages;
    }

    private String toVerboseJson(List<DetectionCandidate> candidates, DocumentResult result) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(new DebugReport(result, candidates));
    }

    private byte[] toBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    private record DebugReport(DocumentResult result, List<DetectionCandidate> candidates) {
    }
}
