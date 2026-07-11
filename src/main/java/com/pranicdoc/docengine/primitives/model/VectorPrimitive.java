package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

/** Common shape for anything the vector-graphics extractor can produce, so detectors can consume a single mixed list. */
public interface VectorPrimitive {
    BoundingBox box();

    int page();
}
