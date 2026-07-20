package com.pranicdoc.docengine.core;

import com.pranicdoc.docengine.classify.DocumentMetadata;
import com.pranicdoc.docengine.graph.DocumentGraph;
import com.pranicdoc.docengine.layout.DocumentLayout;

/** Mutable, per-document context threaded through every pipeline stage. Not thread-safe by itself — one instance per document. */
public final class PipelineContext {

    private final EngineConfig config;
    private DocumentMetadata metadata;
    private int currentPage;
    private DocumentLayout documentLayout;
    private DocumentGraph documentGraph;

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

    public DocumentLayout documentLayout() {
        return documentLayout;
    }

    public void setDocumentLayout(DocumentLayout documentLayout) {
        this.documentLayout = documentLayout;
    }

    public DocumentGraph documentGraph() {
        return documentGraph;
    }

    public void setDocumentGraph(DocumentGraph documentGraph) {
        this.documentGraph = documentGraph;
    }
}
