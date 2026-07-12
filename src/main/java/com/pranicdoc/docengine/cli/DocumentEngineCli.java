package com.pranicdoc.docengine.cli;

import com.pranicdoc.docengine.core.DocumentEngine;
import com.pranicdoc.docengine.debug.DebugRun;
import com.pranicdoc.docengine.output.DocumentResultSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual entry point for trying the engine against a real PDF without writing a test —
 * `gradlew.bat run --args="path/to/form.pdf"`. Not part of the published library's public API;
 * consumers embed DocumentEngine directly instead.
 *
 * Always writes debug artifacts (confidence-colored annotated PDF + per-page PNGs + a verbose
 * JSON dump of every merged candidate) to build/detected-fields/, alongside the plain
 * DocumentResult JSON this always printed.
 */
public final class DocumentEngineCli {

    private static final Path DEBUG_OUTPUT_DIR = Path.of("build", "detected-fields");

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

        DebugRun run = new DocumentEngine().processWithDebug(pdfFile);
        String json = new DocumentResultSerializer().toJson(run.result());

        if (args.length >= 2) {
            Path outputPath = Path.of(args[1]);
            Files.writeString(outputPath, json);
            System.out.println("Wrote " + run.result().fields().size() + " field(s) to " + outputPath.toAbsolutePath());
        } else {
            System.out.println(json);
        }

        String baseName = baseNameOf(pdfFile);
        run.export().writeTo(DEBUG_OUTPUT_DIR, baseName);
        System.out.println("Wrote debug artifacts (" + run.export().annotatedPngPerPage().size() + " page(s)) to "
            + DEBUG_OUTPUT_DIR.toAbsolutePath() + " as " + baseName + "-*");
    }

    private static String baseNameOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
