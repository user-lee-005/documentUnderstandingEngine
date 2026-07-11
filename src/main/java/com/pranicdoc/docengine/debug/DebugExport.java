package com.pranicdoc.docengine.debug;

/** Debug mode is intentionally more verbose than the production DocumentResult JSON — it includes raw per-candidate confidence contributions. */
public record DebugExport(byte[] annotatedPdf, byte[] annotatedPng, String verboseJson) {
}
