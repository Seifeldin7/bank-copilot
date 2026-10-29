package com.banquemisr.copilot.knowledge.ingestion;

import com.banquemisr.copilot.common.ApiException;
import com.banquemisr.copilot.common.FieldError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    public ExtractedDocument extract(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedDocument.Page> pages = new ArrayList<>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ExtractedDocument.Page(page, null, stripper.getText(pdf)));
            }
            return new ExtractedDocument(pdf.getDocumentInformation().getTitle(), pages);
        } catch (IOException e) {
            throw ApiException.badRequest("The PDF could not be read.", new FieldError("file", "This file is not a readable PDF."));
        }
    }
}
