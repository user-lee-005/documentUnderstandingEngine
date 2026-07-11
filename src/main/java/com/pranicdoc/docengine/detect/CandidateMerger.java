package com.pranicdoc.docengine.detect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Non-max suppression over spatially-overlapping candidates: highest raw confidence wins,
 * anything overlapping it above the IoU threshold is dropped. Real conflict resolution
 * (a checkbox inside a table cell inside a signature block) needs a richer policy — this
 * greedy pass is the Roadmap Phase 2 starting point, not the final word.
 */
public class CandidateMerger {

    private static final double MERGE_IOU_THRESHOLD = 0.5;

    public List<DetectionCandidate> merge(List<DetectionCandidate> candidates) {
        List<DetectionCandidate> byConfidenceDesc = new ArrayList<>(candidates);
        byConfidenceDesc.sort(Comparator.comparingDouble(DetectionCandidate::rawConfidence).reversed());

        List<DetectionCandidate> kept = new ArrayList<>();
        for (DetectionCandidate candidate : byConfidenceDesc) {
            boolean overlapsKept = kept.stream()
                .anyMatch(k -> k.page() == candidate.page() && k.box().iou(candidate.box()) >= MERGE_IOU_THRESHOLD);
            if (!overlapsKept) {
                kept.add(candidate);
            }
        }
        return kept;
    }
}
