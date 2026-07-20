package com.pranicdoc.docengine.confidence;

import java.util.List;

public class ConfidenceEngine {

    private final ConfidenceAggregator aggregator;

    public ConfidenceEngine(ConfidenceAggregator aggregator) {
        this.aggregator = aggregator;
    }

    public double score(List<ConfidenceContribution> contributions) {
        return aggregator.aggregate(contributions);
    }
}
