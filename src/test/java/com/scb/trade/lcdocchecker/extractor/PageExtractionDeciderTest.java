package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.PageAnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageExtractionDeciderTest {

    // defaults: minChars=100, minPrintable=0.85, maxReplacement=0.05
    private PageExtractionDecider decider;

    @BeforeEach
    void setUp() {
        decider = new PageExtractionDecider(new PageAnalysisProperties(100, 0.85, 0.05));
    }

    private static PageSignals signals(int chars, double printable, double replacement,
                                       boolean largeRaster, boolean mayHaveContent) {
        return new PageSignals(1, chars, printable, replacement, largeRaster, mayHaveContent);
    }

    @Test
    void usableText_yieldsText() {
        assertThat(decider.decide(signals(200, 0.95, 0.01, true, true)))
                .isEqualTo(ExtractionSource.TEXT);
    }

    @Test
    void boundaryValuesAreInclusive_yieldsText() {
        // chars==min, printable==min, replacement==max → all pass
        assertThat(decider.decide(signals(100, 0.85, 0.05, false, true)))
                .isEqualTo(ExtractionSource.TEXT);
    }

    @Test
    void tooFewChars_notText() {
        assertThat(decider.decide(signals(50, 0.99, 0.0, false, true)))
                .isEqualTo(ExtractionSource.OCR); // text unusable, no raster, may-have → conservative OCR
    }

    @Test
    void lowPrintableRatio_notText() {
        assertThat(decider.decide(signals(200, 0.80, 0.01, false, true)))
                .isEqualTo(ExtractionSource.OCR);
    }

    @Test
    void highReplacementRatio_notText() {
        assertThat(decider.decide(signals(200, 0.95, 0.06, false, true)))
                .isEqualTo(ExtractionSource.OCR);
    }

    @Test
    void unusableTextWithLargeRaster_yieldsOcr() {
        assertThat(decider.decide(signals(10, 0.5, 0.5, true, true)))
                .isEqualTo(ExtractionSource.OCR);
    }

    @Test
    void confirmedBlank_yieldsSkip() {
        // unusable text, no raster, no renderable content → SKIP
        assertThat(decider.decide(signals(0, 1.0, 0.0, false, false)))
                .isEqualTo(ExtractionSource.SKIP);
    }

    @Test
    void uncertainPage_yieldsConservativeOcr_notSkip() {
        // unusable text, no raster, BUT may have content → must OCR, never SKIP
        assertThat(decider.decide(signals(0, 1.0, 0.0, false, true)))
                .isEqualTo(ExtractionSource.OCR);
    }

    @Test
    void nullSignals_yieldsConservativeOcr() {
        assertThat(decider.decide(null)).isEqualTo(ExtractionSource.OCR);
    }

    @Test
    void defaultsApplied_whenPropertiesNull() {
        PageExtractionDecider d = new PageExtractionDecider(new PageAnalysisProperties(null, null, null));
        assertThat(d.decide(signals(200, 0.95, 0.01, false, true))).isEqualTo(ExtractionSource.TEXT);
        assertThat(d.decide(signals(0, 1.0, 0.0, false, false))).isEqualTo(ExtractionSource.SKIP);
    }
}
