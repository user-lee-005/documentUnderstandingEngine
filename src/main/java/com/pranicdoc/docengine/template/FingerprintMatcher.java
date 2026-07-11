package com.pranicdoc.docengine.template;

import java.util.Optional;

public interface FingerprintMatcher {
    Optional<TemplateMatch> findBestMatch(TemplateFingerprint fingerprint, double similarityThreshold);
}
