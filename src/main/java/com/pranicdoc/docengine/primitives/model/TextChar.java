package com.pranicdoc.docengine.primitives.model;

import com.pranicdoc.docengine.geometry.BoundingBox;

public record TextChar(String value, BoundingBox box, String fontName, double fontSize) {
}
