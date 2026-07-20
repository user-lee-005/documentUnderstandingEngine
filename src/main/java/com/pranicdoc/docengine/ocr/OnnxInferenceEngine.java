package com.pranicdoc.docengine.ocr;

/** Backed by ONNX Runtime — the intended slot for a future layout/checkbox inference model, additive to the rule-based detectors, never a replacement for them. Roadmap Phase 4+. */
public interface OnnxInferenceEngine {
    float[] infer(String modelId, float[] inputTensor);
}
