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
                CandidateType.IMAGE_PLACEHOLDER, CandidateType.TABLE)),
        OPTION(EnumSet.allOf(CandidateType.class)),   // no checkbox detector yet — any hit counts
        GRID(EnumSet.of(CandidateType.TABLE, CandidateType.IMAGE_PLACEHOLDER, CandidateType.RECTANGLE));

        final Set<CandidateType> claimableBy;

        Category(Set<CandidateType> claimableBy) {
            this.claimableBy = claimableBy;
        }
    }

    /**
     * Measured baseline (2026-07-12, after GlyphTextRepairer + fieldset LabelDetector +
     * baseline-geometry fix + same-type-only NMS: LABEL 100%, VALUE 57.8%, OPTION 86.7%,
     * GRID 75.0%) minus tolerance. Raise these as Phase 3+ lands. VALUE is the next target:
     * misses cluster in row-band fields (value written under a caption on a divider line,
     * no dedicated detector yet).
     */
    private static final Map<Category, Double> RECALL_FLOOR = new EnumMap<>(Map.of(
            Category.LABEL, 0.95,
            Category.VALUE, 0.55,
            Category.OPTION, 0.80,
            Category.GRID, 0.70));

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
        try (PDDocument doc = Loader.loadPDF(sampleDir.resolve("source.pdf").toFile())) {
            PipelineRunResult run = new PipelineRunner().run(doc, sampleDir.getFileName().toString(), EngineConfig.defaults());
            candidates = run.mergedCandidates();
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
