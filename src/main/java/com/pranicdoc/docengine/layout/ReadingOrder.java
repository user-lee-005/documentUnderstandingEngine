package com.pranicdoc.docengine.layout;

import java.util.List;

/** The sequence of LayoutNode IDs in reading order, as determined by column/row analysis. Roadmap Phase 1. */
public record ReadingOrder(List<String> nodeIdsInOrder) {
}
