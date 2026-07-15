package com.scb.trade.lcdocchecker.extractor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PageTextStatsTest {

    private static final String REPLACEMENT = String.valueOf((char) 0xFFFD);
    private static final String CTRL = String.valueOf((char) 1); // non-whitespace, ISO control

    @Test
    void nullText_yieldsEmptyStats() {
        PageTextStats s = PageTextStats.from(null);
        assertThat(s.nonWhitespaceChars()).isZero();
        assertThat(s.printableRatio()).isEqualTo(1.0);
        assertThat(s.replacementCharRatio()).isEqualTo(0.0);
    }

    @Test
    void whitespaceOnly_yieldsEmptyStats() {
        PageTextStats s = PageTextStats.from("  \t\n\r  ");
        assertThat(s.nonWhitespaceChars()).isZero();
        assertThat(s.printableRatio()).isEqualTo(1.0);
        assertThat(s.replacementCharRatio()).isEqualTo(0.0);
    }

    @Test
    void cleanAscii_allPrintable() {
        PageTextStats s = PageTextStats.from("Hello World 123");
        assertThat(s.nonWhitespaceChars()).isEqualTo(13); // 5 + 5 + 3
        assertThat(s.printableRatio()).isEqualTo(1.0);
        assertThat(s.replacementCharRatio()).isEqualTo(0.0);
    }

    @Test
    void replacementChars_lowerBothRatios() {
        PageTextStats s = PageTextStats.from("ab" + REPLACEMENT + REPLACEMENT);
        assertThat(s.nonWhitespaceChars()).isEqualTo(4);
        assertThat(s.printableRatio()).isEqualTo(0.5); // only a,b are printable
        assertThat(s.replacementCharRatio()).isEqualTo(0.5);
    }

    @Test
    void controlChars_lowerPrintableRatio() {
        PageTextStats s = PageTextStats.from("ab" + CTRL + "cd");
        assertThat(s.nonWhitespaceChars()).isEqualTo(5); // ctrl is non-whitespace
        assertThat(s.printableRatio()).isCloseTo(0.8, within(1e-9)); // 4 of 5 printable
        assertThat(s.replacementCharRatio()).isEqualTo(0.0);
    }

    @Test
    void nonAsciiDefinedCodePoints_arePrintable() {
        PageTextStats s = PageTextStats.from("发票 123");
        assertThat(s.nonWhitespaceChars()).isEqualTo(5); // 发,票,1,2,3
        assertThat(s.printableRatio()).isEqualTo(1.0);
        assertThat(s.replacementCharRatio()).isEqualTo(0.0);
    }
}
