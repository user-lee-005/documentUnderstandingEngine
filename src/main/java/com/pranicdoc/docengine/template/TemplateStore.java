package com.pranicdoc.docengine.template;

import java.util.List;

/** Persistence port — the backing store (Postgres, file-based, ...) is an implementation decision, deferred to Roadmap Phase 5. */
public interface TemplateStore {
    void save(LearnedTemplate template);

    List<LearnedTemplate> findAll();
}
