package com.pranicdoc.docengine.cli;

import com.pranicdoc.docengine.core.DocumentEngine;
import com.pranicdoc.docengine.output.DocumentResult;
import com.pranicdoc.docengine.output.DocumentResultSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual entry point for trying the engine against a real PDF without writing a test —
 * `gradlew.bat run --args="path/to/form.pdf"`. Not part of the published library's public API;
 * consumers embed DocumentEngine directly instead.
 */
public final class DocumentEngineCli {

    private DocumentEngineCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: DocumentEngineCli <path-to-pdf> [output-json-path]");
            System.exit(1);
            return;
        }

        File pdfFile = new File(args[0]);
        if (!pdfFile.isFile()) {
            System.err.println("No such file: " + pdfFile.getAbsolutePath());
            System.exit(1);
            return;
        }

        DocumentResult result = new DocumentEngine().process(pdfFile);
        String json = new DocumentResultSerializer().toJson(result);

        if (args.length >= 2) {
            Path outputPath = Path.of(args[1]);
            Files.writeString(outputPath, json);
            System.out.println("Wrote " + result.fields().size() + " field(s) to " + outputPath.toAbsolutePath());
        } else {
            System.out.println(json);
        }
    }
}
