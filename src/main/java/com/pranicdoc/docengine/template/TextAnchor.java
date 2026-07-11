package com.pranicdoc.docengine.template;

/** A stable text string and its page-relative (0..1) position, used as a fingerprint anchor. */
public record TextAnchor(String text, double normalizedX, double normalizedY) {
}
