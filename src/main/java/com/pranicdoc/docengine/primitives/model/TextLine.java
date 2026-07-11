package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.List;

public record TextLine(String text, BoundingBox box, int page, List<TextWord> words) {
}
