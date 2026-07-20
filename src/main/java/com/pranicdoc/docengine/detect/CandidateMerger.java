package com.pranicdoc.docengine.detect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Non-max suppression over spatially-overlapping candidates <b>of the same type</b>:
 * highest raw confidence wins, anything of that type overlapping it above the IoU threshold
 * is dropped. Different types never suppress each other — a LABEL and a TABLE cell on the
 * same box are two true observations of different things (a table caption), not duplicates;
 * Stage 6's resolver is where cross-type conflicts get decided. Real conflict resolution
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
            boolean duplicateOfKept = kept.stream()
                .anyMatch(k -> k.page() == candidate.page()
                        && k.type() == candidate.type()
                        && k.box().iou(candidate.box()) >= MERGE_IOU_THRESHOLD);
            if (!duplicateOfKept) {
                kept.add(candidate);
            }
        }
        return kept;
    }
}
