package com.pranicdoc.docengine.core;

public interface PipelineStage<I, O> {
    O run(I input, PipelineContext ctx);

    String stageName();
}
