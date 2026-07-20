package com.pranicdoc.docengine.detect;

import com.pranicdoc.docengine.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateMergerTest {

    private static final BoundingBox BOX = new BoundingBox(54, 566, 105, 573);

    @Test
    void duplicateDetectionsOfTheSameTypeCollapseToTheHighestConfidence() {
        DetectionCandidate strong = candidate("rectangle-detector", CandidateType.RECTANGLE, 0.97);
        DetectionCandidate weak = candidate("rectangle-detector", CandidateType.RECTANGLE, 0.60);

        List<DetectionCandidate> merged = new CandidateMerger().merge(List.of(weak, strong));

        assertEquals(1, merged.size());
        assertEquals(0.97, merged.get(0).rawConfidence(), 0.001);
    }

    @Test
    void differentTypesOnTheSameBoxBothSurvive() {
        // A table caption is both a LABEL and inside a TABLE region — two true observations,
        // not duplicates. The lower-confidence LABEL must not be suppressed by the TABLE.
        DetectionCandidate table = candidate("table-detector", CandidateType.TABLE, 0.90);
        DetectionCandidate label = candidate("label-detector", CandidateType.LABEL, 0.85);

        List<DetectionCandidate> merged = new CandidateMerger().merge(List.of(table, label));

        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(c -> c.type() == CandidateType.LABEL));
    }

    @Test
    void sameTypeOnDifferentPagesBothSurvive() {
        DetectionCandidate page0 = new DetectionCandidate("d", BOX, CandidateType.RECTANGLE, 0.9, 0, Map.of());
        DetectionCandidate page1 = new DetectionCandidate("d", BOX, CandidateType.RECTANGLE, 0.9, 1, Map.of());

        assertEquals(2, new CandidateMerger().merge(List.of(page0, page1)).size());
    }

    private static DetectionCandidate candidate(String detectorId, CandidateType type, double confidence) {
        return new DetectionCandidate(detectorId, BOX, type, confidence, 3, Map.of());
    }
}
