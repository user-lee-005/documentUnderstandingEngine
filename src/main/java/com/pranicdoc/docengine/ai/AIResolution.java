package com.pranicdoc.docengine.ai;

public record AIResolution(String resolvedValue, String resolvedType, double confidence, String rationale) {
}
