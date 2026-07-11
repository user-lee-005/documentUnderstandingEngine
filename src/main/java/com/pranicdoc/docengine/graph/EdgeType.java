package com.pranicdoc.docengine.graph;

public enum EdgeType {
    PARENT_OF,
    CHILD_OF,
    READING_ORDER_NEXT,
    NEAREST_LABEL,
    NEAREST_VALUE,
    BELONGS_TO_TABLE,
    TABLE_CELL_AT,
    SAME_REPEATING_GROUP
}
