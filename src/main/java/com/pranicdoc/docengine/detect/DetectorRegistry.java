package com.pranicdoc.docengine.detect;

import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.layout.LayoutNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Fans a LayoutNode scope out to every applicable detector and collects their raw candidates.
 * Detectors never see each other's output — overlap resolution happens afterward in CandidateMerger.
 * Adding a new detector means registering one more FieldCandidateDetector here; nothing else changes.
 */
public class DetectorRegistry {

    private final List<FieldCandidateDetector> detectors;

    public DetectorRegistry(List<FieldCandidateDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    public List<DetectionCandidate> detectAll(LayoutNode scope, PipelineContext ctx) {
        List<DetectionCandidate> all = new ArrayList<>();
        for (FieldCandidateDetector detector : detectors) {
            if (detector.isApplicable(ctx)) {
                all.addAll(detector.detect(scope, ctx));
            }
        }
        return all;
    }
}
