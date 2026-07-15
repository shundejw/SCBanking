package com.scb.trade.lcdocchecker.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Light integration test: the collector must produce "usable text" signals on a real digital
 * page. (The pure text-stat logic is covered by {@link PageTextStatsTest}.)
 */
class PageSignalCollectorTest {

    private static final Path DIGITAL = Path.of("docs/test_fixtures/invoices/invoice-compliant-digital.pdf");

    private final PageSignalCollector collector = new PageSignalCollector();

    @Test
    void digitalPage_signalsUsableText() throws IOException {
        byte[] pdf = Files.readAllBytes(DIGITAL);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDPage page = doc.getPage(0);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String textLayer = stripper.getText(doc);

            PageSignals signals = collector.collect(1, textLayer, page);

            assertThat(signals.nonWhitespaceChars()).isGreaterThan(100);
            assertThat(signals.printableRatio()).isGreaterThanOrEqualTo(0.85);
            assertThat(signals.replacementCharRatio()).isLessThanOrEqualTo(0.05);
            assertThat(signals.mayHaveRenderableContent()).isTrue();
        }
    }
}
