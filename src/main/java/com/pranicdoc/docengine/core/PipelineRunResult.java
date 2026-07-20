package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.detect.DetectionCandidate;
import com.pranicdoc.docengine.output.DocumentResult;

import java.util.List;

/**
 * The full output of one pipeline run: the stable public DocumentResult, plus every merged
 * candidate that fed into it — including ones Stage 6's naive pairing didn't use. The debug
 * renderer needs the latter; DocumentEngine.process() only exposes the former.
 */
public record PipelineRunResult(DocumentResult result, List<DetectionCandidate> mergedCandidates) {
}
