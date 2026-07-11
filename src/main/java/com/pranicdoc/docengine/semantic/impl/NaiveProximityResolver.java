package com.pranicdoc.docengine.semantic.impl;

import com.pranicdoc.docengine.confidence.ConfidenceContribution;
import com.pranicdoc.docengine.confidence.ConfidenceEngine;
import com.pranicdoc.docengine.core.PipelineContext;
import com.pranicdoc.docengine.detect.CandidateType;
import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.semantic.SemanticField;
import com.pranicdoc.docengine.semantic.SemanticResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Roadmap Phase 0's resolver: pairs each LABEL candidate with its nearest same-page
 * RECTANGLE candidate by center distance, with no DocumentGraph and no rule chain.
 * SpatialProximityRule/LabelValuePairingRule (Phase 3) generalize this against the full graph.
 */
public class NaiveProximityResolver implements SemanticResolver {

    private final ConfidenceEngine confidenceEngine;

    public NaiveProximityResolver(ConfidenceEngine confidenceEngine) {
        this.confidenceEngine = confidenceEngine;
    }

    @Override
    public List<SemanticField> resolve(List<DetectionCandidate> mergedCandidates, PipelineContext ctx) {
        double maxDistance = ctx.config().maxLabelToValueDistancePts();

        List<DetectionCandidate> labels = mergedCandidates.stream()
            .filter(c -> c.type() == CandidateType.LABEL)
            .toList();
        List<DetectionCandidate> availableValues = new ArrayList<>(mergedCandidates.stream()
            .filter(c -> c.type() == CandidateType.RECTANGLE)
            .toList());

        List<SemanticField> fields = new ArrayList<>();
        int idx = 0;
        for (DetectionCandidate label : labels) {
            DetectionCandidate nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (DetectionCandidate value : availableValues) {
                if (value.page() != label.page()) {
                    continue;
                }
                double distance = label.box().centerDistanceTo(value.box());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = value;
                }
            }
            if (nearest == null || nearestDistance > maxDistance) {
                continue;
            }
            availableValues.remove(nearest);

            double proximityScore = Math.max(0.0, 1.0 - (nearestDistance / maxDistance));
            double aggregated = confidenceEngine.score(List.of(
                new ConfidenceContribution(label.detectorId(), label.rawConfidence(), ctx.config().weightFor(label.detectorId())),
                new ConfidenceContribution(nearest.detectorId(), nearest.rawConfidence(), ctx.config().weightFor(nearest.detectorId())),
                new ConfidenceContribution("naive-proximity", proximityScore, 1.0)
            ));

            String rawLabelText = (String) label.attributes().getOrDefault("text", "field-" + idx);
            fields.add(new SemanticField(
                "field-" + idx, stripTrailingColon(rawLabelText), "text", null,
                nearest.box(), nearest.page(), aggregated, nearest.detectorId(), List.of()
            ));
            idx++;
        }
        return fields;
    }

    private String stripTrailingColon(String text) {
        return text.endsWith(":") ? text.substring(0, text.length() - 1).trim() : text;
    }
}
