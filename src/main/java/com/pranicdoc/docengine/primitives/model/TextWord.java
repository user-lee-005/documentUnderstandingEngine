package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.List;

public record TextWord(String text, BoundingBox box, List<TextChar> chars) {
}
