package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.primitives.model.TextChar;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GlyphTextRepairerTest {

    private static final String CAPTION_FONT = "ABCDEF+SubsetNoToUnicode";
    private static final String BODY_FONT = "Helvetica";

    @Test
    void decodesStandardMacGlyphOrderIncludingGlyph3SpacesAndApostrophes() {
        // "PATIENT'S NAME" as raw glyph IDs: char = ascii - 29, apostrophe = 183, space = 3
        String encoded = encode("PATIENT") + (char) 183 + encode("S") + (char) 3 + encode("NAME");
        TextLine garbled = lineOf(glyphWord(encoded, CAPTION_FONT));

        List<TextLine> repaired = GlyphTextRepairer.repair(List.of(garbled));

        assertEquals("PATIENT’S NAME", repaired.get(0).text());
        assertEquals(2, repaired.get(0).words().size(), "glyph-3 space must re-split the glued word");
        assertEquals("PATIENT’S", repaired.get(0).words().get(0).text());
        assertEquals("NAME", repaired.get(0).words().get(1).text());
    }

    @Test
    void leavesHealthyFontsUntouchedEvenWhenDollarSignsLookLikeGlyphIds() {
        // "$*(" in a normal font is legitimate text (would decode to "AGE") — no control chars,
        // so the font is never flagged and the text survives verbatim
        TextLine healthy = lineOf(glyphWord("$*(", BODY_FONT));

        List<TextLine> repaired = GlyphTextRepairer.repair(List.of(healthy));

        assertSame(healthy, repaired.get(0));
        assertEquals("$*(", repaired.get(0).text());
    }

    @Test
    void repairsOnlyTheGlyphCodedFontWhenBothAppearInOneLine() {
        String encodedAge = encode("AGE"); // "$*(" as glyph IDs
        TextWord caption = glyphWord(encodedAge + (char) 3 + encode("X"), CAPTION_FONT);
        TextWord value = glyphWord("16", BODY_FONT);
        TextLine mixed = lineOf(caption, value);

        List<TextLine> repaired = GlyphTextRepairer.repair(List.of(mixed));

        assertEquals("AGE X 16", repaired.get(0).text());
    }

    private static String encode(String ascii) {
        StringBuilder sb = new StringBuilder();
        ascii.chars().forEach(c -> sb.append((char) (c - 29)));
        return sb.toString();
    }

    private static TextWord glyphWord(String rawChars, String font) {
        List<TextChar> chars = new ArrayList<>();
        for (int i = 0; i < rawChars.length(); i++) {
            BoundingBox box = new BoundingBox(50 + i * 4, 700, 54 + i * 4, 706);
            chars.add(new TextChar(String.valueOf(rawChars.charAt(i)), box, font, 6.0));
        }
        BoundingBox box = new BoundingBox(50, 700, 50 + rawChars.length() * 4, 706);
        return new TextWord(rawChars, box, chars);
    }

    private static TextLine lineOf(TextWord... words) {
        List<TextWord> list = List.of(words);
        BoundingBox box = BoundingBox.unionOf(list.stream().map(TextWord::box).toList());
        String text = String.join(" ", list.stream().map(TextWord::text).toList());
        return new TextLine(text, box, 0, list);
    }
}
