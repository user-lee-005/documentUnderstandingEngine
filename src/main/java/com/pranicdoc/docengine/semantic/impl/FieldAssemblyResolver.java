package com.pranicdoc.docengine.semantic.impl;

import com.pranicdoc.docengine.confidence.ConfidenceContribution;
import com.pranicdoc.docengine.confidence.ConfidenceEngine;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import com.pranicdoc.docengine.layout.LayoutNode;
import com.pranicdoc.docengine.primitives.model.TextLine;
import com.pranicdoc.docengine.semantic.SemanticField;
import com.pranicdoc.docengine.semantic.SemanticResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 6: assembles merged candidates into resolved fields matching the gold-schema
 * contract. Deterministic, geometry-driven, in this order per page:
 *
 * <ol>
 *   <li><b>Containers</b> — a RECTANGLE/IMAGE_PLACEHOLDER whose top border carries a caption
 *       becomes that caption's field; the value area is the container interior.</li>
 *   <li><b>Checkbox groups</b> — CHECKBOX clusters attach to the field whose container holds
 *       them, else to the caption directly above; lone boxes with text to the right become
 *       single checkboxes (declaration style). The tick is inferred from cluster complexity
 *       (a ticked box has more path segments than its unticked siblings).</li>
 *   <li><b>Underline bands</b> — a caption just below the band (signature style) or a colon
 *       label to its left claims it.</li>
 *   <li><b>Caption bands</b> — CaptionBandDetector output pairs by its recorded caption.</li>
 *   <li><b>Leftovers</b> — remaining captions pair greedily with remaining value areas;
 *       TABLE aggregates become table fields; label-less containers become unlabeled fields.</li>
 * </ol>
 *
 * Values are read from the text lines inside each claimed value area (native PDFs only),
 * numbered captions (SYMPTOM 1..4) fold into one array field, and each field is assigned to
 * the nearest heading above it (large-font ALL-CAPS text) as its section.
 */
public class FieldAssemblyResolver implements SemanticResolver {

    private static final double HEADING_MIN_FONT_PT = 9.5;
    private static final double CONTAINER_CAPTION_TOLERANCE_PTS = 8.0;
    private static final double CHECKBOX_GROUP_CHAIN_GAP_PTS = 60.0;
    private static final double CHECKBOX_CAPTION_MAX_GAP_PTS = 30.0;
    private static final Pattern NUMBERED_CAPTION = Pattern.compile("^(.*\\p{L})\\s+(\\d{1,2})$");
    private static final Pattern DATE_VALUE = Pattern.compile("\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}");

    private final ConfidenceEngine confidenceEngine;

    public FieldAssemblyResolver(ConfidenceEngine confidenceEngine) {
        this.confidenceEngine = confidenceEngine;
    }

    @Override
    public List<SemanticField> resolve(List<DetectionCandidate> mergedCandidates, PipelineContext ctx) {
        Map<Integer, List<DetectionCandidate>> byPage = new LinkedHashMap<>();
        for (DetectionCandidate c : mergedCandidates) {
            byPage.computeIfAbsent(c.page(), k -> new ArrayList<>()).add(c);
        }

        List<SemanticField> all = new ArrayList<>();
        for (Map.Entry<Integer, List<DetectionCandidate>> e : byPage.entrySet()) {
            all.addAll(resolvePage(e.getKey(), e.getValue(), ctx));
        }
        return all;
    }

    // ---------------------------------------------------------------- per page

    private List<SemanticField> resolvePage(int page, List<DetectionCandidate> candidates, PipelineContext ctx) {
        List<DetectionCandidate> captions = new ArrayList<>();
        List<Heading> headings = new ArrayList<>();
        for (DetectionCandidate c : candidates) {
            if (c.type() != CandidateType.LABEL) {
                continue;
            }
            double fontSize = ((Number) c.attributes().getOrDefault("fontSize", 0.0)).doubleValue();
            String text = (String) c.attributes().getOrDefault("text", "");
            boolean hasGlyphBox = text.chars().anyMatch(ch -> isGlyphCheckbox(String.valueOf((char) ch)));
            if (fontSize >= HEADING_MIN_FONT_PT && isMostlyUppercase(text) && text.length() >= 4 && !hasGlyphBox) {
                headings.add(new Heading(text, c.box()));
            } else if (c.rawConfidence() >= 0.8 && !hasGlyphBox) {
                captions.add(c);
            }
            // low-confidence legacy labels are usually filled-in values, not captions — ignored
        }

        List<DetectionCandidate> containers = new ArrayList<>();
        List<DetectionCandidate> checkboxes = new ArrayList<>();
        List<DetectionCandidate> underlines = new ArrayList<>();
        List<DetectionCandidate> captionBands = new ArrayList<>();
        List<DetectionCandidate> tables = new ArrayList<>();
        List<DetectionCandidate> vlines = new ArrayList<>();
        for (DetectionCandidate c : candidates) {
            switch (c.type()) {
                case RECTANGLE, IMAGE_PLACEHOLDER -> {
                    if (!Boolean.TRUE.equals(c.attributes().get("filled"))) {
                        containers.add(c); // filled rects are banners/shading, not writable areas
                    }
                }
                case CHECKBOX -> checkboxes.add(c);
                case UNDERLINE -> underlines.add(c);
                case VLINE -> vlines.add(c);
                case WHITESPACE -> {
                    if (c.attributes().containsKey("caption")) {
                        captionBands.add(c);
                    }
                }
                case TABLE -> {
                    if (Boolean.TRUE.equals(c.attributes().get("aggregate"))) {
                        tables.add(c);
                    }
                }
                default -> { }
            }
        }

        // a caption that sits INSIDE a checkbox is that box's option name, not a field caption
        captions.removeIf(l -> checkboxes.stream().anyMatch(cb -> contains(cb.box(), l.box())));
        captions = mergeMultiLineCaptions(captions);

        List<TextLine> pageText = pageTextLines(page, ctx);
        Set<DetectionCandidate> claimedCaptions = new HashSet<>();
        List<Draft> drafts = new ArrayList<>();

        // 1. containers with a caption on their top border
        Set<DetectionCandidate> claimedContainers = new HashSet<>();
        for (DetectionCandidate container : containers) {
            DetectionCandidate caption = captions.stream()
                    .filter(l -> !claimedCaptions.contains(l))
                    .filter(l -> Math.abs(l.box().centerY() - container.box().y1()) <= CONTAINER_CAPTION_TOLERANCE_PTS)
                    .filter(l -> l.box().x0() >= container.box().x0() - 5 && l.box().x0() < container.box().x1())
                    .min(Comparator.comparingDouble(l -> l.box().x0()))
                    .orElse(null);
            if (caption == null) {
                continue;
            }
            claimedCaptions.add(caption);
            claimedContainers.add(container);
            // sub-fields live inside big containers (CITY inside the ADDRESS block): the outer
            // field's value area stops above the topmost caption drawn inside the container
            double interiorBottom = container.box().y0() + 3;
            for (DetectionCandidate inner : captions) {
                if (inner != caption && contains(container.box(), inner.box())
                        && inner.box().y1() < container.box().y1() - 12) {
                    interiorBottom = Math.max(interiorBottom, inner.box().y1() + 2);
                }
            }
            BoundingBox interior = BoundingBox.of(
                    container.box().x0() + 4, interiorBottom,
                    container.box().x1() - 4, container.box().y1() - 10);
            drafts.add(Draft.labelled(caption, container, interior));
        }

        // 2. checkboxes: attach to their containing field, else group by nearest caption above,
        //    else chain leftovers into label-less groups / declaration-style singles
        assignCheckboxes(checkboxes, drafts, captions, claimedCaptions, pageText);

        // 3. underline bands claimed by adjacent captions; one drawn line can serve several
        //    side-by-side captions (SIGNATURE ... DATE under one rule) — split it between them
        Set<DetectionCandidate> claimedValues = new HashSet<>(claimedContainers);
        for (DetectionCandidate band : underlines) {
            List<DetectionCandidate> below = captionsBelowUnderline(band, captions, claimedCaptions, pageText);
            if (!below.isEmpty()) {
                claimedValues.add(band);
                for (int i = 0; i < below.size(); i++) {
                    DetectionCandidate caption = below.get(i);
                    claimedCaptions.add(caption);
                    double sliceX0 = Math.max(band.box().x0(), caption.box().x0() - 2);
                    double sliceX1 = i + 1 < below.size() ? below.get(i + 1).box().x0() - 4 : band.box().x1();
                    BoundingBox slice = BoundingBox.of(sliceX0, band.box().y0(), sliceX1, band.box().y1());
                    drafts.add(Draft.labelled(caption, band, slice));
                }
                continue;
            }
            DetectionCandidate left = captionLeftOfUnderline(band, captions, claimedCaptions);
            if (left != null) {
                claimedCaptions.add(left);
                claimedValues.add(band);
                drafts.add(Draft.labelled(left, band, band.box()));
            }
        }

        // 4. caption bands (writable area beneath a caption) for still-unclaimed captions
        for (DetectionCandidate band : captionBands) {
            String captionText = (String) band.attributes().get("caption");
            DetectionCandidate caption = captions.stream()
                    .filter(l -> !claimedCaptions.contains(l))
                    .filter(l -> normalize(captionText).equals(normalize((String) l.attributes().get("text"))))
                    .filter(l -> l.box().y0() >= band.box().y1() - 4)
                    .min(Comparator.comparingDouble(l -> l.box().y0() - band.box().y1()))
                    .orElse(null);
            if (caption != null) {
                claimedCaptions.add(caption);
                claimedValues.add(band);
                drafts.add(Draft.labelled(caption, band, band.box()));
            }
        }

        // 5a. leftover captions pair greedily with leftover containers
        double maxDistance = ctx.config().maxLabelToValueDistancePts();
        for (DetectionCandidate caption : captions) {
            if (claimedCaptions.contains(caption)) {
                continue;
            }
            DetectionCandidate nearest = containers.stream()
                    .filter(v -> !claimedValues.contains(v))
                    .min(Comparator.comparingDouble(v -> caption.box().centerDistanceTo(v.box())))
                    .orElse(null);
            if (nearest != null && caption.box().centerDistanceTo(nearest.box()) <= maxDistance) {
                claimedCaptions.add(caption);
                claimedValues.add(nearest);
                drafts.add(Draft.labelled(caption, nearest, nearest.box()));
            }
        }

        // 5b. still-unclaimed colon captions ("Client's Name:") own the writing band to their
        //     RIGHT — Word-style forms leave that space blank, so no primitive candidate exists.
        //     If a drawn column-divider rule sits between the caption and that space (a design
        //     column separating labels from values), the band starts just past the rule instead
        //     of hugging the caption text; with no rule, it defaults to right next to the label.
        for (DetectionCandidate caption : captions) {
            if (claimedCaptions.contains(caption) || !isColonLikeCaption(String.valueOf(caption.attributes().get("text")))) {
                continue;
            }
            double right = captions.stream()
                    .filter(o -> o != caption && o.box().x0() > caption.box().x1())
                    .filter(o -> Math.min(o.box().y1(), caption.box().y1()) - Math.max(o.box().y0(), caption.box().y0())
                            > caption.box().height() * 0.5)
                    .mapToDouble(o -> o.box().x0() - 4)
                    .min().orElse(Math.min(caption.box().x1() + 280, pageBox(page, ctx).x1() - 30));
            if (right - caption.box().x1() < 20) {
                continue;
            }
            double left = vlines.stream()
                    .filter(v -> v.box().x0() > caption.box().x1() && v.box().x0() < right)
                    .filter(v -> v.box().y0() <= caption.box().centerY() && v.box().y1() >= caption.box().centerY())
                    .mapToDouble(v -> v.box().x0() + 3)
                    .min().orElse(caption.box().x1() + 3);
            if (right - left < 20) {
                left = caption.box().x1() + 3;
            }
            claimedCaptions.add(caption);
            drafts.add(Draft.labelled(caption, null,
                    BoundingBox.of(left, caption.box().y0() - 2, right, caption.box().y1() + 3)));
        }

        // 5b. tables
        for (DetectionCandidate table : tables) {
            drafts.add(Draft.table(table));
        }

        // 5c. label-less leftover containers still carry placement value
        for (DetectionCandidate container : containers) {
            if (!claimedValues.contains(container) && container.type() == CandidateType.RECTANGLE) {
                drafts.add(Draft.unlabelled(container));
            }
        }

        // read values, then materialize
        Set<BoundingBox> reservedText = new HashSet<>();
        captions.forEach(c -> reservedText.add(c.box()));
        headings.forEach(h -> reservedText.add(h.box()));

        List<SemanticField> fields = new ArrayList<>();
        for (Draft draft : drafts) {
            fields.add(materialize(draft, page, pageText, reservedText, headings, ctx));
        }
        fields = foldNumberedCaptionsIntoArrays(fields, captions);
        fields.sort(Comparator.comparingDouble((SemanticField f) -> -(referenceBox(f).y1()))
                .thenComparingDouble(f -> referenceBox(f).x0()));
        return fields;
    }

    // ---------------------------------------------------------------- checkboxes

    private void assignCheckboxes(List<DetectionCandidate> checkboxes, List<Draft> drafts,
                                  List<DetectionCandidate> captions, Set<DetectionCandidate> claimedCaptions,
                                  List<TextLine> pageText) {
        List<DetectionCandidate> leftovers = new ArrayList<>();
        Map<Draft, List<DetectionCandidate>> byContainer = new LinkedHashMap<>();
        Map<DetectionCandidate, List<DetectionCandidate>> byCaption = new LinkedHashMap<>();

        for (DetectionCandidate box : checkboxes) {
            Draft home = drafts.stream()
                    .filter(d -> d.container != null && contains(d.container.box(), box.box()))
                    .findFirst().orElse(null);
            if (home != null) {
                byContainer.computeIfAbsent(home, k -> new ArrayList<>()).add(box);
                continue;
            }
            // nearest caption above this individual box — GENDER's circles go to GENDER even
            // when MARITAL STATUS's boxes sit 15pt away in the same band — or a colon caption
            // to the LEFT on the same line ("Type of Healing:  [] Simple  [] Complex")
            DetectionCandidate caption = captions.stream()
                    .filter(l -> l.box().y0() >= box.box().y1() - 2
                            && l.box().y0() - box.box().y1() <= CHECKBOX_CAPTION_MAX_GAP_PTS)
                    .min(Comparator.comparingDouble(l -> horizontalDistance(l.box(), box.box().centerX())))
                    .filter(l -> horizontalDistance(l.box(), box.box().centerX()) < 60)
                    .orElse(null);
            if (caption == null) {
                caption = captions.stream()
                        .filter(l -> {
                            String t = String.valueOf(l.attributes().get("text"));
                            return t.endsWith(":") || t.endsWith("?");
                        })
                        .filter(l -> l.box().y0() < box.box().y1() && l.box().y1() > box.box().y0())
                        .filter(l -> l.box().x1() <= box.box().x0() && box.box().x0() - l.box().x1() < 250)
                        .min(Comparator.comparingDouble(l -> box.box().x0() - l.box().x1()))
                        .orElse(null);
            }
            if (caption != null) {
                byCaption.computeIfAbsent(caption, k -> new ArrayList<>()).add(box);
            } else {
                leftovers.add(box);
            }
        }

        for (Map.Entry<Draft, List<DetectionCandidate>> e : byContainer.entrySet()) {
            e.getKey().options.addAll(toOptions(sortedByX(e.getValue()), pageText));
        }
        for (Map.Entry<DetectionCandidate, List<DetectionCandidate>> e : byCaption.entrySet()) {
            claimedCaptions.add(e.getKey());
            Draft draft = Draft.labelled(e.getKey(), e.getValue().get(0), null);
            draft.options.addAll(toOptions(sortedByX(e.getValue()), pageText));
            drafts.add(draft);
        }
        for (DetectionCandidate box : leftovers) {
            String sentence = pageText.stream()
                    .filter(l -> l.box().x0() >= box.box().x1() - 2 && l.box().x0() - box.box().x1() < 15)
                    .filter(l -> l.box().y0() < box.box().y1() && l.box().y1() > box.box().y0())
                    .map(TextLine::text).findFirst().orElse(null);
            drafts.add(Draft.singleCheckbox(box, sentence, isChecked(box, false, 1)));
        }
    }

    private static List<DetectionCandidate> sortedByX(List<DetectionCandidate> boxes) {
        return boxes.stream().sorted(Comparator.comparingDouble(b -> b.box().x0())).toList();
    }

    private static double horizontalDistance(BoundingBox caption, double x) {
        if (x >= caption.x0() && x <= caption.x1()) {
            return 0;
        }
        return Math.min(Math.abs(x - caption.x0()), Math.abs(x - caption.x1()));
    }

    /** Merges captions stacked directly on top of each other (two-line captions). */
    private List<DetectionCandidate> mergeMultiLineCaptions(List<DetectionCandidate> captions) {
        List<DetectionCandidate> sorted = new ArrayList<>(captions.stream()
                .sorted(Comparator.comparingDouble((DetectionCandidate c) -> -c.box().y1())).toList());
        List<DetectionCandidate> result = new ArrayList<>();
        while (!sorted.isEmpty()) {
            DetectionCandidate top = sorted.remove(0);
            String text = (String) top.attributes().get("text");
            BoundingBox box = top.box();
            boolean merged = true;
            while (merged) {
                merged = false;
                for (DetectionCandidate other : sorted) {
                    boolean sameLeft = Math.abs(other.box().x0() - box.x0()) <= 4;
                    boolean directlyBelow = box.y0() - other.box().y1() >= -1 && box.y0() - other.box().y1() <= 6;
                    if (sameLeft && directlyBelow) {
                        text = text + " " + other.attributes().get("text");
                        box = BoundingBox.of(Math.min(box.x0(), other.box().x0()), other.box().y0(),
                                Math.max(box.x1(), other.box().x1()), box.y1());
                        sorted.remove(other);
                        merged = true;
                        break;
                    }
                }
            }
            Map<String, Object> attrs = new HashMap<>(top.attributes());
            attrs.put("text", text);
            result.add(new DetectionCandidate(top.detectorId(), box, top.type(), top.rawConfidence(),
                    top.page(), attrs, top.scopeNodeId()));
        }
        return result;
    }

    private List<SemanticField.Option> toOptions(List<DetectionCandidate> group, List<TextLine> pageText) {
        int minMembers = group.stream().mapToInt(c -> ((Number) c.attributes().getOrDefault("members", 1)).intValue()).min().orElse(1);
        List<SemanticField.Option> options = new ArrayList<>();
        int i = 1;
        for (DetectionCandidate box : group) {
            boolean selected = isChecked(box, group.size() > 1, minMembers);
            String name = pageText.stream()
                    .flatMap(l -> l.words().stream())
                    .filter(w -> !isGlyphCheckbox(w.text()))
                    .filter(w -> contains(box.box(), w.box()))
                    .map(w -> w.text()).reduce((a, b) -> a + " " + b)
                    // glyph checkboxes contain only the glyph — the option name is the word right of it
                    .or(() -> pageText.stream()
                            .flatMap(l -> l.words().stream())
                            .filter(w -> !isGlyphCheckbox(w.text()))
                            .filter(w -> w.box().x0() >= box.box().x1() - 1 && w.box().x0() - box.box().x1() < 25)
                            .filter(w -> w.box().y0() < box.box().y1() && w.box().y1() > box.box().y0())
                            .min(Comparator.comparingDouble(w -> w.box().x0()))
                            .map(w -> w.text()))
                    .orElse("option-" + i);
            options.add(new SemanticField.Option(name, box.box(), selected));
            i++;
        }
        return options;
    }

    /** A glyph checkbox carries its own state; vector clusters fall back to the members heuristic. */
    private static boolean isChecked(DetectionCandidate box, boolean inGroup, int groupMinMembers) {
        Object explicit = box.attributes().get("checked");
        if (explicit instanceof Boolean b) {
            return b;
        }
        int members = ((Number) box.attributes().getOrDefault("members", 1)).intValue();
        return inGroup ? members > groupMinMembers : members >= 3;
    }

    private static boolean isGlyphCheckbox(String text) {
        return text.length() == 1 && "☐□▢❑❒☑☒■▣"
                .indexOf(text.charAt(0)) >= 0;
    }

    /** Mirrors LabelDetector's "delimited" signal: a colon, question mark, trailing parenthetical
     * hint, or inline underscore blank all mark a caption whose write area sits to its right. */
    private static boolean isColonLikeCaption(String text) {
        return text.endsWith(":") || text.endsWith("?")
                || (text.endsWith(")") && text.contains("(")) || text.endsWith("__");
    }

    // ---------------------------------------------------------------- underlines

    /** A caption this close above a line owns the band above it — the fieldset-divider pattern. */
    private static final double COMPETING_CAPTION_LOOKUP_PTS = 40.0;

    /**
     * Captions sitting on/under the band's line in the SIGNATURE style: the value is written
     * ABOVE the line, the caption below it. A fieldset divider has the exact same geometry
     * (caption straddles the line dead-center), so geometry cannot distinguish the two — the
     * discriminator is content: the signature pattern has words above the line and nothing in
     * the caption's own below-region; a fieldset caption's value sits below it. On a blank
     * form both regions are empty and the caption falls through to its caption-band instead.
     */
    private List<DetectionCandidate> captionsBelowUnderline(DetectionCandidate band, List<DetectionCandidate> captions,
                                                            Set<DetectionCandidate> claimed, List<TextLine> pageText) {
        boolean bandBelongsToCaptionAbove = captions.stream()
                .anyMatch(c -> c.box().y0() >= band.box().y0()
                        && c.box().y0() - band.box().y0() <= COMPETING_CAPTION_LOOKUP_PTS
                        && c.box().centerY() > band.box().y0() + 2
                        && overlapX(c.box(), band.box()) > 10);
        if (bandBelongsToCaptionAbove || !hasWordsIn(band.box(), pageText)) {
            return List.of();
        }
        return captions.stream()
                .filter(c -> !claimed.contains(c))
                .filter(c -> Math.abs(c.box().centerY() - band.box().y0()) <= 6)
                .filter(c -> overlapX(c.box(), band.box()) > 10)
                .filter(c -> !hasWordsIn(BoundingBox.of(c.box().x0(), c.box().y0() - 19,
                        Math.min(band.box().x1(), c.box().x0() + 150), c.box().y0() - 2), pageText))
                .sorted(Comparator.comparingDouble(c -> c.box().x0()))
                .toList();
    }

    /** Only value-sized words count as evidence — captions/hints/footers are all smaller. */
    private boolean hasWordsIn(BoundingBox box, List<TextLine> pageText) {
        for (TextLine line : pageText) {
            for (var word : line.words()) {
                double fontSize = word.chars().isEmpty() ? 0 : word.chars().get(0).fontSize();
                if (fontSize < MIN_VALUE_FONT_PT) {
                    continue;
                }
                BoundingBox b = word.box();
                if (b.centerX() >= box.x0() && b.centerX() <= box.x1()
                        && b.centerY() >= box.y0() && b.centerY() <= box.y1()) {
                    return true;
                }
            }
        }
        return false;
    }

    private DetectionCandidate captionLeftOfUnderline(DetectionCandidate band, List<DetectionCandidate> captions,
                                                      Set<DetectionCandidate> claimed) {
        return captions.stream()
                .filter(c -> !claimed.contains(c))
                .filter(c -> Math.abs(c.box().y0() - band.box().y0()) < 10)
                .filter(c -> c.box().x1() <= band.box().x0() + 6 && band.box().x0() - c.box().x1() < 40)
                .findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- materialization

    private SemanticField materialize(Draft draft, int page, List<TextLine> pageText,
                                      Set<BoundingBox> reservedText, List<Heading> headings, PipelineContext ctx) {
        String label = draft.label;
        BoundingBox valueBox = draft.valueBox;
        String value = null;
        if (valueBox != null && draft.options.isEmpty() && !"table".equals(draft.type)) {
            value = readValue(valueBox, pageText, reservedText);
        }
        if ("checkbox".equals(draft.type)) {
            value = String.valueOf(draft.options.get(0).selected());
        } else if (!draft.options.isEmpty() && value == null) {
            value = draft.options.stream().filter(SemanticField.Option::selected)
                    .map(SemanticField.Option::name).reduce((a, b) -> a + ", " + b).orElse(null);
        }

        String type = inferType(draft, label, value);
        String section = nearestHeadingAbove(headings, referenceBoxOf(draft));

        double labelConf = draft.labelCandidate != null ? draft.labelCandidate.rawConfidence() : 0.5;
        double valueConf = draft.valueCandidate != null ? draft.valueCandidate.rawConfidence() : 0.5;
        double confidence = confidenceEngine.score(List.of(
                new ConfidenceContribution("label", labelConf, 1.0),
                new ConfidenceContribution("value", valueConf, 1.0)));

        List<SemanticField.Option> options = "checkbox".equals(draft.type) ? List.of() : List.copyOf(draft.options);
        return new SemanticField(
                "field-" + page + "-" + Math.abs(referenceBoxOf(draft).hashCode() % 100000),
                label, type, value, valueBox,
                draft.labelCandidate != null ? draft.labelCandidate.box() : null,
                page, section,
                draft.valueCandidate != null ? draft.valueCandidate.scopeNodeId() : null,
                confidence,
                draft.valueCandidate != null ? draft.valueCandidate.detectorId() : "resolver",
                options, List.of(), List.of());
    }

    /** Printed hint text ("e.g. ...", "Please state...") is small; filled-in values are 9pt+. */
    private static final double MIN_VALUE_FONT_PT = 8.5;

    private String readValue(BoundingBox valueBox, List<TextLine> pageText, Set<BoundingBox> reservedText) {
        List<String> parts = new ArrayList<>();
        for (TextLine line : pageText) {
            for (var word : line.words()) {
                BoundingBox b = word.box();
                boolean centerInside = b.centerX() >= valueBox.x0() && b.centerX() <= valueBox.x1()
                        && b.centerY() >= valueBox.y0() && b.centerY() <= valueBox.y1();
                if (!centerInside || reservedText.stream().anyMatch(r -> contains(r, b))) {
                    continue;
                }
                double fontSize = word.chars().isEmpty() ? MIN_VALUE_FONT_PT
                        : word.chars().get(0).fontSize();
                if (fontSize < MIN_VALUE_FONT_PT) {
                    continue;
                }
                parts.add(word.text());
            }
        }
        String value = String.join(" ", parts).trim();
        return value.isEmpty() ? null : value;
    }

    private String inferType(Draft draft, String label, String value) {
        if ("table".equals(draft.type)) {
            return "table";
        }
        if ("checkbox".equals(draft.type)) {
            return "checkbox";
        }
        if (!draft.options.isEmpty()) {
            return "checkbox-group";
        }
        String l = label == null ? "" : label.toUpperCase();
        if (l.contains("SIGNATURE")) {
            return "signature";
        }
        if (l.contains("DATE") || (value != null && DATE_VALUE.matcher(value).find())) {
            return "date";
        }
        if (value != null && value.matches("\\d+")) {
            return "number";
        }
        return "text";
    }

    private List<SemanticField> foldNumberedCaptionsIntoArrays(List<SemanticField> fields, List<DetectionCandidate> captions) {
        Map<String, List<SemanticField>> byStem = new LinkedHashMap<>();
        for (SemanticField f : fields) {
            if (f.name() == null || !f.options().isEmpty()) {
                continue;
            }
            Matcher m = NUMBERED_CAPTION.matcher(f.name());
            if (m.matches() && m.group(1).length() >= 3) {
                byStem.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(f);
            }
        }

        List<SemanticField> result = new ArrayList<>(fields);
        for (Map.Entry<String, List<SemanticField>> e : byStem.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            List<SemanticField.Item> items = new ArrayList<>();
            for (SemanticField f : e.getValue()) {
                Matcher m = NUMBERED_CAPTION.matcher(f.name());
                if (!m.matches()) {
                    continue;
                }
                items.add(new SemanticField.Item(Integer.parseInt(m.group(2)), f.labelBox(), f.box(), f.value()));
            }
            items.sort(Comparator.comparingInt(SemanticField.Item::index));
            result.removeAll(e.getValue());

            String stem = e.getKey();
            DetectionCandidate groupCaption = captions.stream()
                    .filter(c -> {
                        String t = (String) c.attributes().get("text");
                        return t != null && normalize(t).equals(normalize(stem) + "S");
                    })
                    .findFirst().orElse(null);
            String arrayName = groupCaption != null ? (String) groupCaption.attributes().get("text") : stem;
            SemanticField first = e.getValue().get(0);
            BoundingBox groupBox = BoundingBox.unionOf(items.stream()
                    .map(i -> i.valueBox() != null ? i.valueBox() : i.labelBox()).filter(b -> b != null).toList());
            // the group caption's own container field (SYMPTOMS box holding SYMPTOM 1..4) is
            // subsumed by the array — drop it so the field isn't reported twice
            int page = first.page();
            result.removeIf(f -> f.items().isEmpty() && f.name() != null && f.page() == page
                    && normalize(f.name()).equals(normalize(arrayName)));
            result.add(new SemanticField(first.id() + "-array", arrayName, "array", null, groupBox,
                    groupCaption != null ? groupCaption.box() : first.labelBox(),
                    first.page(), first.sectionName(), first.sectionNodeId(),
                    first.confidence(), first.detectorId(), List.of(), items, List.of()));
        }
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private BoundingBox pageBox(int page, PipelineContext ctx) {
        if (ctx.documentLayout() != null) {
            for (LayoutNode root : ctx.documentLayout().pageRoots()) {
                if (root.page() == page && root.box() != null) {
                    return root.box();
                }
            }
        }
        return BoundingBox.of(0, 0, 612, 792);
    }

    private List<TextLine> pageTextLines(int page, PipelineContext ctx) {
        if (ctx.documentLayout() == null) {
            return List.of();
        }
        for (LayoutNode root : ctx.documentLayout().pageRoots()) {
            if (root.page() == page) {
                List<TextLine> lines = root.attribute("textLines");
                return lines == null ? List.of() : lines;
            }
        }
        return List.of();
    }

    private String nearestHeadingAbove(List<Heading> headings, BoundingBox ref) {
        return headings.stream()
                .filter(h -> h.box().y0() >= ref.y1() - 2)
                .min(Comparator.comparingDouble(h -> h.box().y0() - ref.y1()))
                .map(Heading::text).orElse(null);
    }

    private BoundingBox referenceBox(SemanticField f) {
        return f.box() != null ? f.box() : (f.labelBox() != null ? f.labelBox()
                : f.options().isEmpty() ? BoundingBox.of(0, 0, 0, 0) : f.options().get(0).box());
    }

    private BoundingBox referenceBoxOf(Draft d) {
        if (d.valueBox != null) {
            return d.valueBox;
        }
        if (d.labelCandidate != null) {
            return d.labelCandidate.box();
        }
        return d.valueCandidate != null ? d.valueCandidate.box() : BoundingBox.of(0, 0, 0, 0);
    }

    private static boolean isMostlyUppercase(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        if (letters < 2) {
            return false;
        }
        return text.chars().filter(Character::isUpperCase).count() >= letters * 0.8;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase();
    }

    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return inner.x0() >= outer.x0() - 1 && inner.x1() <= outer.x1() + 1
                && inner.y0() >= outer.y0() - 1 && inner.y1() <= outer.y1() + 1;
    }

    private static boolean intersects(BoundingBox a, BoundingBox b) {
        return a.x0() < b.x1() && a.x1() > b.x0() && a.y0() < b.y1() && a.y1() > b.y0();
    }

    private static double overlapX(BoundingBox a, BoundingBox b) {
        return Math.min(a.x1(), b.x1()) - Math.max(a.x0(), b.x0());
    }

    private record Heading(String text, BoundingBox box) {
    }

    /** Mutable assembly scratchpad for one field. */
    private static final class Draft {
        String label;
        String type; // null=infer, "checkbox", "table"
        DetectionCandidate labelCandidate;
        DetectionCandidate valueCandidate;
        DetectionCandidate container; // set only for caption-on-container fields
        BoundingBox valueBox;
        final List<SemanticField.Option> options = new ArrayList<>();

        static Draft labelled(DetectionCandidate caption, DetectionCandidate value, BoundingBox valueBox) {
            Draft d = new Draft();
            d.label = stripColon((String) caption.attributes().get("text"));
            d.labelCandidate = caption;
            d.valueCandidate = value;
            d.valueBox = valueBox;
            if (value != null && (value.type() == CandidateType.RECTANGLE || value.type() == CandidateType.IMAGE_PLACEHOLDER)) {
                d.container = value;
            }
            return d;
        }

        static Draft table(DetectionCandidate aggregate) {
            Draft d = new Draft();
            d.type = "table";
            d.valueCandidate = aggregate;
            d.valueBox = aggregate.box();
            return d;
        }

        static Draft unlabelled(DetectionCandidate container) {
            Draft d = new Draft();
            d.valueCandidate = container;
            d.container = container;
            d.valueBox = container.box();
            return d;
        }

        static Draft unlabelledGroup(DetectionCandidate first) {
            Draft d = new Draft();
            d.valueCandidate = first;
            return d;
        }

        static Draft singleCheckbox(DetectionCandidate box, String sentence, boolean checked) {
            Draft d = new Draft();
            d.label = sentence;
            d.type = "checkbox";
            d.valueCandidate = box;
            d.valueBox = box.box();
            d.options.add(new SemanticField.Option("checked", box.box(), checked));
            return d;
        }

        private static String stripColon(String text) {
            return text != null && text.endsWith(":") ? text.substring(0, text.length() - 1).trim() : text;
        }
    }
}
