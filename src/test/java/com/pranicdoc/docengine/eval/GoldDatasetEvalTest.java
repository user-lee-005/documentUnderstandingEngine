package com.pranicdoc.docengine.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.docengine.core.EngineConfig;
import com.pranicdoc.docengine.core.PipelineRunResult;
import com.pranicdoc.docengine.core.PipelineRunner;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.geometry.BoundingBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scores the engine's merged candidate list against the gold dataset
 * (src/test/resources/gold/&lt;sample&gt;/{source.pdf, expected.json}).
 *
 * <p>This measures <b>detection recall</b>: for every ground-truth box (caption strip, value
 * area, checkbox target, table grid), did any candidate of a compatible type land on it?
 * Field-level precision/recall (right label text, right pairing, right value) needs the
 * Phase 3 resolver + final schema and is not scored yet.
 *
 * <p>Accuracy claims about this engine mean nothing except as scores here. The RECALL_FLOOR
 * values are the measured baseline at the time of writing minus a small tolerance — raise
 * them as detection improves; a drop below a floor is a regression.
 */
class GoldDatasetEvalTest {

    private static final Path GOLD_ROOT = Path.of("src", "test", "resources", "gold");

    /** Ground-truth box categories and which candidate types may claim them. */
    private enum Category {
        LABEL(EnumSet.of(CandidateType.LABEL)),
        VALUE(EnumSet.of(CandidateType.RECTANGLE, CandidateType.UNDERLINE, CandidateType.WHITESPACE,
                CandidateType.IMAGE_PLACEHOLDER, CandidateType.TABLE, CandidateType.CHECKBOX)),
        OPTION(EnumSet.allOf(CandidateType.class)),   // no checkbox detector yet — any hit counts
        GRID(EnumSet.of(CandidateType.TABLE, CandidateType.IMAGE_PLACEHOLDER, CandidateType.RECTANGLE));

        final Set<CandidateType> claimableBy;

        Category(Set<CandidateType> claimableBy) {
            this.claimableBy = claimableBy;
        }
    }

    /**
     * Measured baseline (2026-07-12, after the detection-recall push: CaptionBandDetector,
     * native CheckboxDetector, underline write-bands, CTM fix in VectorPrimitiveExtractor,
     * rule-boundary row folding, aggregate table candidates): 100% in every category on cl01.
     * Floors sit just below so real regressions fail loudly while allowing minor jitter from
     * future gold samples.
     */
    private static final Map<Category, Double> RECALL_FLOOR = new EnumMap<>(Map.of(
            Category.LABEL, 0.95,
            Category.VALUE, 0.95,
            Category.OPTION, 0.95,
            Category.GRID, 0.95));

    private record Target(int page, BoundingBox box, Category category, String name) {
    }

    @TestFactory
    Stream<DynamicTest> everyGoldSampleScoresAboveBaseline() throws IOException {
        assertTrue(Files.isDirectory(GOLD_ROOT), "gold dataset missing: " + GOLD_ROOT.toAbsolutePath());
        List<Path> samples;
        try (Stream<Path> dirs = Files.list(GOLD_ROOT)) {
            samples = dirs.filter(d -> Files.exists(d.resolve("source.pdf"))
                    && Files.exists(d.resolve("expected.json"))).toList();
        }
        assertFalse(samples.isEmpty(), "no gold samples under " + GOLD_ROOT.toAbsolutePath());
        return samples.stream().map(dir -> DynamicTest.dynamicTest("gold/" + dir.getFileName(), () -> score(dir)));
    }

    private void score(Path sampleDir) throws IOException {
        List<Target> targets = loadTargets(sampleDir.resolve("expected.json"));
        assertFalse(targets.isEmpty(), "expected.json produced no ground-truth boxes");

        List<DetectionCandidate> candidates;
        com.pranicdoc.docengine.output.DocumentResult result;
        try (PDDocument doc = Loader.loadPDF(sampleDir.resolve("source.pdf").toFile())) {
            PipelineRunResult run = new PipelineRunner().run(doc, sampleDir.getFileName().toString(), EngineConfig.defaults());
            candidates = run.mergedCandidates();
            result = run.result();
        }

        StringBuilder report = new StringBuilder();
        report.append("Gold eval — ").append(sampleDir.getFileName()).append('\n');
        report.append("candidates: ").append(candidates.size()).append(", ground-truth boxes: ").append(targets.size()).append("\n\n");

        Map<Category, int[]> tally = new EnumMap<>(Category.class); // [hit, total]
        List<Target> misses = new ArrayList<>();
        for (Target t : targets) {
            boolean hit = candidates.stream().anyMatch(c -> c.page() == t.page()
                    && t.category().claimableBy.contains(c.type())
                    && hits(c.box(), t.box()));
            int[] row = tally.computeIfAbsent(t.category(), k -> new int[2]);
            row[1]++;
            if (hit) {
                row[0]++;
            } else {
                misses.add(t);
            }
        }

        report.append(String.format("%-8s %5s %5s %7s   floor%n", "category", "hit", "total", "recall"));
        for (Map.Entry<Category, int[]> e : tally.entrySet()) {
            double recall = e.getValue()[0] / (double) e.getValue()[1];
            report.append(String.format(Locale.ROOT, "%-8s %5d %5d %6.1f%%   %.0f%%%n",
                    e.getKey(), e.getValue()[0], e.getValue()[1], recall * 100, RECALL_FLOOR.get(e.getKey()) * 100));
        }
        report.append('\n').append("misses (page | category | field):\n");
        misses.forEach(t -> report.append(String.format("  p%d | %-6s | %s%n", t.page(), t.category(), t.name())));

        FieldScore fieldScore = scoreFields(sampleDir.resolve("expected.json"), result, report);

        Path out = Path.of("build", "reports", "gold-eval");
        Files.createDirectories(out);
        Files.writeString(out.resolve(sampleDir.getFileName() + ".txt"), report.toString());
        System.out.println(report);

        for (Map.Entry<Category, int[]> e : tally.entrySet()) {
            double recall = e.getValue()[0] / (double) e.getValue()[1];
            assertTrue(recall >= RECALL_FLOOR.get(e.getKey()),
                    e.getKey() + " recall " + recall + " fell below baseline floor " + RECALL_FLOOR.get(e.getKey())
                            + " — detection regressed (see build/reports/gold-eval)");
        }
        assertTrue(fieldScore.fieldRecall() >= FIELD_RECALL_FLOOR,
                "field recall " + fieldScore.fieldRecall() + " below floor " + FIELD_RECALL_FLOOR);
        assertTrue(fieldScore.valueAccuracy() >= VALUE_ACCURACY_FLOOR,
                "value accuracy " + fieldScore.valueAccuracy() + " below floor " + VALUE_ACCURACY_FLOOR);
    }

    // ---- field-level scoring: right label <-> right box <-> right value ----

    /** Measured 2026-07-12 on cl01: fields matched 98.1%, values 100%. Floors just below. */
    private static final double FIELD_RECALL_FLOOR = 0.95;
    private static final double VALUE_ACCURACY_FLOOR = 0.95;

    private record FieldScore(double fieldRecall, double valueAccuracy) {
    }

    private FieldScore scoreFields(Path expectedJson, com.pranicdoc.docengine.output.DocumentResult result,
                                   StringBuilder report) throws IOException {
        JsonNode root = new ObjectMapper().readTree(expectedJson.toFile());
        List<com.pranicdoc.docengine.output.FieldResult> resolved = result.allFields();

        int goldFields = 0;
        int matched = 0;
        int valueChecks = 0;
        int valueCorrect = 0;
        List<String> problems = new ArrayList<>();

        for (JsonNode page : root.path("pages")) {
            int pageNo = page.path("page").asInt();
            for (JsonNode section : page.path("sections")) {
                for (JsonNode f : section.path("fields")) {
                    BoundingBox labelBox = box(f.get("labelBox"));
                    if (labelBox == null || "table".equals(f.path("type").asText())) {
                        continue; // tables are scored as GRID; label-less golds can't be matched by label
                    }
                    goldFields++;
                    String goldLabel = f.path("label").asText();
                    com.pranicdoc.docengine.output.FieldResult match = resolved.stream()
                            .filter(r -> r.page() == pageNo && r.labelBox() != null && hits(r.labelBox(), labelBox))
                            .findFirst().orElse(null);
                    if (match == null) {
                        problems.add("UNMATCHED p" + pageNo + " " + goldLabel);
                        continue;
                    }
                    matched++;

                    // value scoring: scalar fields with a non-null gold value; checkbox groups by
                    // whether the selected option's box overlaps the gold value option's box
                    JsonNode goldValue = f.get("value");
                    if (goldValue != null && !goldValue.isNull() && f.path("options").isMissingNode()) {
                        valueChecks++;
                        if (valueMatches(goldValue.asText(), match.value())) {
                            valueCorrect++;
                        } else {
                            problems.add("VALUE p" + pageNo + " " + goldLabel + ": want "
                                    + goldValue.asText() + " got " + match.value());
                        }
                    } else if (f.has("options") && goldValue != null && !goldValue.isNull()) {
                        BoundingBox goldOptionBox = null;
                        for (JsonNode o : f.path("options")) {
                            if (goldValue.asText().equals(o.path("name").asText())) {
                                goldOptionBox = box(o.get("box"));
                            }
                        }
                        if (goldOptionBox != null) {
                            valueChecks++;
                            BoundingBox finalGoldOptionBox = goldOptionBox;
                            boolean ok = match.options().stream()
                                    .anyMatch(o -> o.selected() && (hits(o.box(), finalGoldOptionBox)
                                            || goldValue.asText().equalsIgnoreCase(o.name())));
                            if (ok || String.valueOf(true).equals(String.valueOf(match.value())) && "true".equals(goldValue.asText())) {
                                valueCorrect++;
                            } else {
                                problems.add("SELECT p" + pageNo + " " + goldLabel + ": want " + goldValue.asText());
                            }
                        }
                    }
                    // array items by index
                    for (JsonNode item : f.path("items")) {
                        JsonNode iv = item.get("value");
                        if (iv == null || iv.isNull()) {
                            continue;
                        }
                        valueChecks++;
                        int idx = item.path("index").asInt();
                        String got = match.items().stream().filter(i -> i.index() == idx)
                                .map(com.pranicdoc.docengine.output.FieldResult.ItemResult::value)
                                .findFirst().orElse(null);
                        if (valueMatches(iv.asText(), got)) {
                            valueCorrect++;
                        } else {
                            problems.add("ITEM p" + pageNo + " " + goldLabel + "#" + idx + ": want " + iv.asText() + " got " + got);
                        }
                    }
                }
            }
        }

        double fieldRecall = goldFields == 0 ? 1.0 : matched / (double) goldFields;
        double valueAccuracy = valueChecks == 0 ? 1.0 : valueCorrect / (double) valueChecks;
        report.append(String.format(Locale.ROOT,
                "%nfield-level: matched %d/%d (%.1f%%), values correct %d/%d (%.1f%%)%n",
                matched, goldFields, fieldRecall * 100, valueCorrect, valueChecks, valueAccuracy * 100));
        problems.forEach(p -> report.append("  ").append(p).append('\n'));
        return new FieldScore(fieldRecall, valueAccuracy);
    }

    private static boolean valueMatches(String gold, String got) {
        String g = normalizeValue(gold);
        String r = normalizeValue(got);
        if (g.equals(r)) {
            return true;
        }
        // tolerate render truncation ("Test Prot" vs "Test Protocol") in either direction
        return g.length() >= 4 && r.length() >= 4 && (g.startsWith(r) || r.startsWith(g));
    }

    private static String normalizeValue(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static BoundingBox box(JsonNode node) {
        if (node == null || !node.isArray() || node.size() != 4) {
            return null;
        }
        return new BoundingBox(node.get(0).asDouble(), node.get(1).asDouble(),
                node.get(2).asDouble(), node.get(3).asDouble());
    }

    /** Hit = candidate covers ≥50% of the ground-truth box, or IoU ≥ 0.3. */
    private static boolean hits(BoundingBox c, BoundingBox t) {
        double ix = Math.max(0, Math.min(c.x1(), t.x1()) - Math.max(c.x0(), t.x0()));
        double iy = Math.max(0, Math.min(c.y1(), t.y1()) - Math.max(c.y0(), t.y0()));
        double inter = ix * iy;
        if (inter <= 0) {
            return false;
        }
        double areaT = (t.x1() - t.x0()) * (t.y1() - t.y0());
        double areaC = (c.x1() - c.x0()) * (c.y1() - c.y0());
        double coverage = areaT > 0 ? inter / areaT : 0;
        double iou = inter / (areaT + areaC - inter);
        return coverage >= 0.5 || iou >= 0.3;
    }

    // ---- expected.json walking ----

    private static List<Target> loadTargets(Path expectedJson) throws IOException {
        JsonNode root = new ObjectMapper().readTree(expectedJson.toFile());
        List<Target> targets = new ArrayList<>();
        for (JsonNode page : root.path("pages")) {
            int pageNo = page.path("page").asInt();
            for (JsonNode section : page.path("sections")) {
                for (JsonNode f : section.path("fields")) {
                    String name = f.path("label").asText();
                    addBox(targets, pageNo, f.get("labelBox"), Category.LABEL, name);
                    addBox(targets, pageNo, f.get("valueBox"), Category.VALUE, name);
                    for (JsonNode o : f.path("options")) {
                        addBox(targets, pageNo, o.get("box"), Category.OPTION, name + " [" + o.path("name").asText() + "]");
                    }
                    for (JsonNode it : f.path("items")) {
                        String itemName = name + " #" + it.path("index").asInt();
                        addBox(targets, pageNo, it.get("labelBox"), Category.LABEL, itemName);
                        addBox(targets, pageNo, it.get("valueBox"), Category.VALUE, itemName);
                    }
                    if (f.has("grid")) {
                        addBox(targets, pageNo, f.path("grid").get("bbox"), Category.GRID, name);
                    }
                    for (JsonNode slot : f.path("symptomNameSlots")) {
                        String slotName = name + " symptomName#" + slot.path("index").asInt();
                        addBox(targets, pageNo, slot.get("labelBox"), Category.LABEL, slotName);
                        addBox(targets, pageNo, slot.get("valueBox"), Category.VALUE, slotName);
                    }
                }
            }
        }
        return targets;
    }

    private static void addBox(List<Target> targets, int page, JsonNode box, Category cat, String name) {
        if (box == null || !box.isArray() || box.size() != 4) {
            return;
        }
        targets.add(new Target(page,
                new BoundingBox(box.get(0).asDouble(), box.get(1).asDouble(), box.get(2).asDouble(), box.get(3).asDouble()),
                cat, name));
    }
}
