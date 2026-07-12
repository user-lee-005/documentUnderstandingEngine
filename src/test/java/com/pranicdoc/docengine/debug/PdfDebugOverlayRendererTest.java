package com.pranicdoc.docengine.debug;

import com.pranicdoc.docengine.core.DocumentEngine;
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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the debugger end to end through DocumentEngine.processWithDebug — the same path the
 * CLI uses to populate build/detected-fields. Also demonstrates, concretely, that multiple
 * boxes detected in the same row are tracked as an array of independent candidates, not merged
 * into one input: this fixture draws two rectangles near one label, only the nearer one is
 * close enough to pair into a final field, and the farther one still shows up in the verbose
 * debug JSON as its own untouched candidate.
 */
class PdfDebugOverlayRendererTest {

    @Test
    void twoBoxesInOneRowAreTrackedAsSeparateCandidatesNotOneMergedInput(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("synthetic.pdf").toFile();
        buildSyntheticPdf(pdfFile);

        DebugRun run = new DocumentEngine().processWithDebug(pdfFile);

        // Only the nearer rectangle is close enough (within maxLabelToValueDistancePts) to pair
        // with "Name:" — NaiveProximityResolver is strict 1-label-to-1-value, nearest only.
        assertEquals(1, run.result().fields().size());
        assertEquals("Name", run.result().fields().get(0).name());

        DebugExport export = run.export();
        assertTrue(export.annotatedPdf().length > 0);
        assertEquals(1, export.annotatedPngPerPage().size());

        // Both rectangles were detected as independent candidates — the far one just never won
        // a pairing. The debug JSON is the only place it's visible; DocumentResult drops it.
        long rectangleCandidateCount = countOccurrences(export.verboseJson(), "\"detectorId\" : \"rectangle-detector\"");
        assertEquals(2, rectangleCandidateCount);
        assertTrue(export.verboseJson().contains("\"candidates\""));
        assertTrue(export.verboseJson().contains("\"result\""));
    }

    @Test
    void writeToSavesAnnotatedPdfPerPagePngsAndDebugJson(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("synthetic.pdf").toFile();
        buildSyntheticPdf(pdfFile);

        DebugRun run = new DocumentEngine().processWithDebug(pdfFile);
        Path outputDir = tempDir.resolve("detected-fields");
        run.export().writeTo(outputDir, "synthetic");

        assertTrue(Files.exists(outputDir.resolve("synthetic-annotated.pdf")));
        assertTrue(Files.exists(outputDir.resolve("synthetic-page-0.png")));
        assertTrue(Files.exists(outputDir.resolve("synthetic-debug.json")));
    }

    private long countOccurrences(String haystack, String needle) {
        long count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
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
                cs.addRect(100, 696, 120, 20); // close enough to "Name:" to pair
                cs.stroke();
                // Only 5pt away from the first box (well under the column-gutter threshold, so
                // ColumnDetector keeps both in the same column/row) but far enough from the label
                // itself that it falls outside maxLabelToValueDistancePts — it stays unpaired.
                cs.addRect(225, 696, 120, 20);
                cs.stroke();
            }

            document.save(file);
        }
    }
}
