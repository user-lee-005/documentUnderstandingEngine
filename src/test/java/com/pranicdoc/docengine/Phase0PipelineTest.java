package com.pranicdoc.docengine;

import com.pranicdoc.docengine.core.DocumentEngine;
import com.pranicdoc.docengine.output.DocumentResult;
import com.pranicdoc.docengine.output.FieldResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the Roadmap Phase 0 slice: a synthetic single-page PDF with
 * one colon-terminated label ("Name:") and one nearby stroked rectangle should come out
 * the other end as exactly one paired, positively-confident field.
 */
class Phase0PipelineTest {

    @Test
    void detectsAndPairsALabelWithANearbyRectangle(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("synthetic.pdf").toFile();
        buildSyntheticPdf(pdfFile);

        DocumentResult result = new DocumentEngine().process(pdfFile);

        assertEquals(1, result.pageCount());
        assertEquals(1, result.allFields().size());

        FieldResult field = result.allFields().get(0);
        assertEquals("Name", field.label());
        assertTrue(field.confidence() > 0.0, "expected a positive confidence score, got " + field.confidence());
        assertNotNull(field.valueBox());
    }

    private void buildSyntheticPdf(File file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Name:");
                cs.endText();

                cs.setLineWidth(1f);
                cs.addRect(100, 696, 120, 20);
                cs.stroke();
            }

            document.save(file);
        }
    }
}
