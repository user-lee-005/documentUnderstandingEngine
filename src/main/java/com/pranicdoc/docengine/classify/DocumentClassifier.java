package com.pranicdoc.docengine.classify;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;

public interface DocumentClassifier {
    DocumentMetadata classify(PDDocument document) throws IOException;
}
