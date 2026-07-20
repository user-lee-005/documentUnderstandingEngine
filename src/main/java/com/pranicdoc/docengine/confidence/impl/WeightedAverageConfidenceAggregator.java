package com.pranicdoc.docengine.confidence.impl;

import com.pranicdoc.docengine.confidence.ConfidenceAggregator;
import com.pranicdoc.docengine.confidence.ConfidenceContribution;

import java.util.List;

/**
 * Default aggregation strategy. Treats every contribution's score as commensurable —
 * a known simplification (geometric and OCR confidence aren't really the same kind of
 * uncertainty); source-type-aware aggregation is Roadmap Phase 4.
 */
public class WeightedAverageConfidenceAggregator implements ConfidenceAggregator {

    @Override
    public double aggregate(List<ConfidenceContribution> contributions) {
        if (contributions.isEmpty()) {
            return 0.0;
        }
        double weightedSum = contributions.stream().mapToDouble(c -> c.score() * c.weight()).sum();
        double totalWeight = contributions.stream().mapToDouble(ConfidenceContribution::weight).sum();
        return totalWeight <= 0.0 ? 0.0 : weightedSum / totalWeight;
    }
}
