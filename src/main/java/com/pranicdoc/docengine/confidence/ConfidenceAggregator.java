package com.pranicdoc.docengine.confidence;

import java.util.List;

public interface ConfidenceAggregator {
    double aggregate(List<ConfidenceContribution> contributions);
}
