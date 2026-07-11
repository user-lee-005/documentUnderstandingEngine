package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.primitives.model.ImagePrimitive;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImagePrimitiveExtractorTest {

    @Test
    void extractsThePlacedBoundingBoxOfAnEmbeddedImage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            BufferedImage buffered = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            PDImageXObject image = LosslessFactory.createFromImage(document, buffered);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.drawImage(image, 100, 200, 150, 80);
            }

            PipelineContext ctx = new PipelineContext(EngineConfig.defaults());
            List<ImagePrimitive> images = new ImagePrimitiveExtractor().extract(document, 0, ctx);

            assertEquals(1, images.size());
            ImagePrimitive primitive = images.get(0);
            assertEquals(100, primitive.box().x0(), 0.5);
            assertEquals(200, primitive.box().y0(), 0.5);
            assertEquals(250, primitive.box().x1(), 0.5);
            assertEquals(280, primitive.box().y1(), 0.5);
            assertEquals(10, primitive.pixelWidth());
            assertEquals(10, primitive.pixelHeight());
        }
    }
}
