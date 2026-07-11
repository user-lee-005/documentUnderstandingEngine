package com.pranicdoc.docengine.ai;

/**
 * Never the default path — only EscalationPolicy calls this. Swappable backend: whether
 * it's a local LLM, a hosted API, or a human-review queue is behind this one interface.
 * Roadmap Phase 6.
 */
public interface AIFallbackGateway {
    AIResolution resolve(AICandidatePayload payload);
}
