package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.classify.DocumentMetadata;

/** Mutable, per-document context threaded through every pipeline stage. Not thread-safe by itself — one instance per document. */
public final class PipelineContext {

    private final EngineConfig config;
    private DocumentMetadata metadata;
    private int currentPage;

    public PipelineContext(EngineConfig config) {
        this.config = config;
    }

    public EngineConfig config() {
        return config;
    }

    public DocumentMetadata metadata() {
        return metadata;
    }

    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }

    public int currentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
}
