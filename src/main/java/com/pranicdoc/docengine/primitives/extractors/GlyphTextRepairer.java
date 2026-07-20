package com.pranicdoc.docengine.primitives.extractors;

import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.primitives.model.TextChar;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.primitives.model.TextWord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs text from fonts that lack a usable ToUnicode CMap.
 *
 * <p>Some embedded subset fonts (notably the caption font in pdfWorker-generated CL01 forms)
 * make PDFBox's {@code TextPosition.getUnicode()} return raw glyph IDs as characters —
 * "AGE" extracts as {@code $*(} (glyph IDs 36/42/40). For TrueType fonts that keep the
 * <b>standard Macintosh glyph ordering</b>, glyph ID {@code g} in [3..97] maps to ASCII
 * {@code g + 29} (space is glyph 3, 'A' is 36, '~' is 97).
 *
 * <p>Detection is conservative, per font: a font is only treated as glyph-coded when its
 * extracted text contains control characters (the giveaway is glyph-3 "spaces" arriving as
 * {@code }), and the remapped text must be overwhelmingly printable — otherwise the
 * font's text is left untouched. Geometry is never altered; glyph-3 chars become real
 * spaces, which re-splits words that PDFBox glued together.
 */
public final class GlyphTextRepairer {

    /** Standard-Mac-ordering glyph IDs above the contiguous ASCII run that we care about. */
    private static final Map<Integer, String> EXTRA_GLYPHS = Map.of(
            183, "’",  // quoteright — the apostrophe in "PATIENT'S"
            184, "‘",  // quoteleft
            178, "–",  // endash
            179, "—"); // emdash

    private static final double MIN_PRINTABLE_RATIO = 0.85;

    private GlyphTextRepairer() {
    }

    public static List<TextLine> repair(List<TextLine> lines) {
        Set<String> glyphCodedFonts = detectGlyphCodedFonts(lines);
        if (glyphCodedFonts.isEmpty()) {
            return lines;
        }
        List<TextLine> repaired = new ArrayList<>(lines.size());
        for (TextLine line : lines) {
            repaired.add(repairLine(line, glyphCodedFonts));
        }
        return repaired;
    }

    /**
     * A font is glyph-coded when it emits control characters (raw glyph IDs below 0x20 that
     * are not real whitespace) AND remapping its entire text yields ≥85% printable chars.
     */
    private static Set<String> detectGlyphCodedFonts(List<TextLine> lines) {
        Map<String, List<String>> charsByFont = new HashMap<>();
        for (TextLine line : lines) {
            for (TextWord word : line.words()) {
                for (TextChar ch : word.chars()) {
                    charsByFont.computeIfAbsent(ch.fontName(), k -> new ArrayList<>()).add(ch.value());
                }
            }
        }
        Set<String> suspicious = new HashSet<>();
        for (Map.Entry<String, List<String>> e : charsByFont.entrySet()) {
            boolean hasControlChars = e.getValue().stream().anyMatch(GlyphTextRepairer::isControl);
            if (!hasControlChars) {
                continue;
            }
            long printableAfter = e.getValue().stream().map(GlyphTextRepairer::remap)
                    .filter(GlyphTextRepairer::isPlausibleText).count();
            if (printableAfter >= e.getValue().size() * MIN_PRINTABLE_RATIO) {
                suspicious.add(e.getKey());
            }
        }
        return suspicious;
    }

    private static TextLine repairLine(TextLine line, Set<String> glyphCodedFonts) {
        boolean touched = line.words().stream().flatMap(w -> w.chars().stream())
                .anyMatch(c -> glyphCodedFonts.contains(c.fontName()));
        if (!touched) {
            return line;
        }
        // Remap chars, then rebuild words: repaired glyph-3 spaces split glued words.
        List<TextWord> words = new ArrayList<>();
        List<TextChar> current = new ArrayList<>();
        for (TextWord word : line.words()) {
            for (TextChar ch : word.chars()) {
                String value = glyphCodedFonts.contains(ch.fontName()) ? remap(ch.value()) : ch.value();
                if (value.isBlank()) {
                    flush(current, words);
                    continue;
                }
                current.add(new TextChar(value, ch.box(), ch.fontName(), ch.fontSize()));
            }
            flush(current, words);
        }
        if (words.isEmpty()) {
            return line;
        }
        String text = String.join(" ", words.stream().map(TextWord::text).toList());
        return new TextLine(text, line.box(), line.page(), List.copyOf(words));
    }

    private static void flush(List<TextChar> current, List<TextWord> words) {
        if (current.isEmpty()) {
            return;
        }
        BoundingBox box = BoundingBox.unionOf(current.stream().map(TextChar::box).toList());
        String text = String.join("", current.stream().map(TextChar::value).toList());
        words.add(new TextWord(text, box, List.copyOf(current)));
        current.clear();
    }

    private static String remap(String value) {
        if (value.length() != 1) {
            return value;
        }
        int code = value.charAt(0);
        if (code >= 3 && code <= 97) {
            return String.valueOf((char) (code + 29));
        }
        return EXTRA_GLYPHS.getOrDefault(code, value);
    }

    private static boolean isControl(String value) {
        return value.length() == 1 && value.charAt(0) < 0x20
                && !Character.isWhitespace(value.charAt(0));
    }

    private static boolean isPlausibleText(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char c = value.charAt(0);
        return c == ' ' || Character.isLetterOrDigit(c)
                || (c >= '!' && c <= '~') || EXTRA_GLYPHS.containsValue(value);
    }
}
