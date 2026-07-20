package com.pranicdoc.docengine.classify;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic classifier: counts extracted text characters and embedded image XObjects
 * per page. No rasterization or OCR happens here — that's deferred to Phase 4 detectors
 * that actually need pixel data.
 */
public class PdfDocumentClassifier implements DocumentClassifier {

    /** Below this many extracted characters per page, a page is treated as image-only. */
    private static final int MIN_TEXT_CHARS_PER_PAGE_FOR_NATIVE = 20;

    @Override
    public DocumentMetadata classify(PDDocument document) throws IOException {
        List<PageDimensions> pages = new ArrayList<>();
        boolean hasEmbeddedImages = false;
        boolean hasVectorGraphics = false;
        int totalTextChars = 0;

        PDFTextStripper stripper = new PDFTextStripper();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage page = document.getPage(i);
            pages.add(new PageDimensions(
                page.getMediaBox().getWidth(),
                page.getMediaBox().getHeight(),
                page.getRotation()
            ));

            stripper.setStartPage(i + 1);
            stripper.setEndPage(i + 1);
            String pageText = stripper.getText(document);
            int pageChars = pageText.replaceAll("\\s", "").length();
            totalTextChars += pageChars;

            for (COSNameHolder holder : xObjectNames(page)) {
                if (holder.isImage()) {
                    hasEmbeddedImages = true;
                } else {
                    hasVectorGraphics = true;
                }
            }
        }

        DocumentType type = classifyType(document, totalTextChars, hasEmbeddedImages);

        return new DocumentMetadata(document.getNumberOfPages(), pages, hasEmbeddedImages, hasVectorGraphics, totalTextChars, type);
    }

    private DocumentType classifyType(PDDocument document, int totalTextChars, boolean hasEmbeddedImages) {
        int expectedMinChars = document.getNumberOfPages() * MIN_TEXT_CHARS_PER_PAGE_FOR_NATIVE;
        boolean looksTextless = totalTextChars < expectedMinChars;

        if (looksTextless && hasEmbeddedImages) {
            return DocumentType.SCANNED;
        }
        if (!looksTextless && hasEmbeddedImages) {
            return DocumentType.HYBRID;
        }
        return DocumentType.NATIVE;
    }

    private List<COSNameHolder> xObjectNames(PDPage page) throws IOException {
        List<COSNameHolder> holders = new ArrayList<>();
        if (page.getResources() == null) {
            return holders;
        }
        for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames()) {
            PDXObject xObject = page.getResources().getXObject(name);
            holders.add(new COSNameHolder(xObject instanceof PDImageXObject));
        }
        return holders;
    }

    private record COSNameHolder(boolean isImage) {
    }
}
