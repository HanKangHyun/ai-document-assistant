package com.hankang.aidocumentassistant;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentTextExtractor {

    public List<PageText> extractPages(File pdfFile) throws IOException {

        List<PageText> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= document.getNumberOfPages(); page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String text = stripper.getText(document)
                        .replaceAll("\\s+", " ")
                        .trim();

                pages.add(new PageText(page, text));
            }
        }

        return pages;
    }

    public record PageText(
            int pageNumber,
            String text
    ) {}
}