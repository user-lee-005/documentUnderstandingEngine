package com.pranicdoc.docengine.debug;

import com.pranicdoc.docengine.output.DocumentResult;

/** Bundles the stable public result with the richer debug export, so callers can print/save the plain JSON without re-running the pipeline. */
public record DebugRun(DocumentResult result, DebugExport export) {
}
