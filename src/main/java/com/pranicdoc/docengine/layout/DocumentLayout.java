package com.pranicdoc.docengine.layout;

import java.util.List;

/** One root LayoutNode and one ReadingOrder per page, index-aligned. */
public record DocumentLayout(List<LayoutNode> pageRoots, List<ReadingOrder> readingOrders) {
}
