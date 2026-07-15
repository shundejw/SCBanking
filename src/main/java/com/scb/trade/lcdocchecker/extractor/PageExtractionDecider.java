package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.PageAnalysisProperties;
import org.springframework.stereotype.Component;

/**
 * Pure per-page extraction-strategy decider. Thresholds are injected so calibration is testable
 * and configurable. Risk preference is CONSERVATIVE (financial-document bias toward never losing
 * a page): only a page confirmed to have no renderable content is SKIPped; everything uncertain
 * goes to OCR. Image presence ({@code hasLargeRasterCandidate}) is a supporting signal only — it
 * never triggers OCR on its own, so a logo/qr/stamp on a digital page is not mistaken for a scan.
 *
 * <p>Boundary semantics are inclusive: a value equal to the threshold satisfies the condition.
 *
 * <pre>
 *   textUsable                              → TEXT
 *   else hasLargeRasterCandidate            → OCR
 *   else !mayHaveRenderableContent          → SKIP   // confirmed blank
 *   else                                    → OCR    // uncertain → conservative OCR
 * </pre>
 */
@Component
public class PageExtractionDecider {

    private final PageAnalysisProperties props;

    public PageExtractionDecider(PageAnalysisProperties props) {
        this.props = props;
    }

    public ExtractionSource decide(PageSignals signals) {
        if (signals == null) {
            return ExtractionSource.OCR; // cannot prove a page is empty → OCR
        }
        if (textUsable(signals)) {
            return ExtractionSource.TEXT;
        }
        if (signals.hasLargeRasterCandidate()) {
            return ExtractionSource.OCR;
        }
        if (!signals.mayHaveRenderableContent()) {
            return ExtractionSource.SKIP;
        }
        return ExtractionSource.OCR;
    }

    private boolean textUsable(PageSignals s) {
        return s.nonWhitespaceChars() >= props.minNonWhitespaceChars()
                && s.printableRatio() >= props.minPrintableRatio()
                && s.replacementCharRatio() <= props.maxReplacementCharRatio();
    }
}
