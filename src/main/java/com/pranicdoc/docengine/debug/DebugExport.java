package com.pranicdoc.docengine.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Debug mode is intentionally more verbose than the production DocumentResult JSON — verboseJson
 * carries every merged candidate (including ones Stage 6's naive pairing never used), not just
 * the fields that survived it. One PNG per page since a multi-page PDF can't be flattened into
 * a single raster image.
 */
public record DebugExport(byte[] annotatedPdf, List<byte[]> annotatedPngPerPage, String verboseJson) {

    /** Writes {@code <baseName>-annotated.pdf}, {@code <baseName>-page-N.png} per page, and {@code <baseName>-debug.json} into directory, creating it if needed. */
    public void writeTo(Path directory, String baseName) throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve(baseName + "-annotated.pdf"), annotatedPdf);
        for (int i = 0; i < annotatedPngPerPage.size(); i++) {
            Files.write(directory.resolve(baseName + "-page-" + i + ".png"), annotatedPngPerPage.get(i));
        }
        Files.writeString(directory.resolve(baseName + "-debug.json"), verboseJson);
    }
}
