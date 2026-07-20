package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.primitives.model.TextChar;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the char -> word -> line hierarchy directly from PDFBox glyph positions
 * (overriding writeString's TextPosition callback), preserving positional fidelity
 * that a plain getText() call would discard.
 */
public class TextPrimitiveExtractor implements PrimitiveExtractor<TextLine> {

    @Override
    public List<TextLine> extract(PDDocument document, int pageIndex, PipelineContext ctx) throws IOException {
        double pageHeightPts = document.getPage(pageIndex).getMediaBox().getHeight();
        LineCapturingStripper stripper = new LineCapturingStripper(pageIndex, pageHeightPts);
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);
        return GlyphTextRepairer.repair(stripper.lines());
    }

    private static final class LineCapturingStripper extends PDFTextStripper {

        private final int pageIndex;
        private final double pageHeightPts;
        private final List<TextLine> lines = new ArrayList<>();

        LineCapturingStripper(int pageIndex, double pageHeightPts) throws IOException {
            super();
            this.pageIndex = pageIndex;
            this.pageHeightPts = pageHeightPts;
        }

        List<TextLine> lines() {
            return lines;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            List<TextWord> words = new ArrayList<>();
            List<TextChar> currentWord = new ArrayList<>();

            for (TextPosition tp : textPositions) {
                String unicode = tp.getUnicode();
                // U+200B: Word exports pepper zero-width spaces through text; Java does not
                // treat them as whitespace, so isBlank() alone would keep them as value chars
                if (unicode == null || unicode.isBlank() || unicode.equals("​")) {
                    flushWord(currentWord, words);
                    continue;
                }
                currentWord.add(toTextChar(tp));
            }
            flushWord(currentWord, words);

            if (words.isEmpty()) {
                return;
            }

            BoundingBox lineBox = BoundingBox.unionOf(words.stream().map(TextWord::box).toList());
            String lineText = words.stream().map(TextWord::text).collect(Collectors.joining(" "));
            lines.add(new TextLine(lineText, lineBox, pageIndex, List.copyOf(words)));
        }

        private void flushWord(List<TextChar> currentWord, List<TextWord> words) {
            if (currentWord.isEmpty()) {
                return;
            }
            BoundingBox wordBox = BoundingBox.unionOf(currentWord.stream().map(TextChar::box).toList());
            String wordText = currentWord.stream().map(TextChar::value).collect(Collectors.joining());
            words.add(new TextWord(wordText, wordBox, List.copyOf(currentWord)));
            currentWord.clear();
        }

        private TextChar toTextChar(TextPosition tp) {
            // tp.getY() is the BASELINE in top-down display coords; glyphs extend upward from it.
            double baselineY = pageHeightPts - tp.getY();
            BoundingBox box = BoundingBox.of(tp.getX(), baselineY, tp.getX() + tp.getWidth(), baselineY + tp.getHeight());
            String fontName = tp.getFont() != null ? tp.getFont().getName() : "unknown";
            return new TextChar(tp.getUnicode(), box, fontName, tp.getFontSizeInPt());
        }
    }
}
