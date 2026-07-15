package com.scb.trade.lcdocchecker.extractor;

/**
 * Pure per-page text-layer statistics computed over Unicode code points. The denominator for
 * both ratios is the non-whitespace code-point count (shared, so the two thresholds stay
 * comparable). A page with no non-whitespace code points reports {@code printableRatio=1.0} and
 * {@code replacementCharRatio=0.0} (no evidence of bad characters) — its usability is then decided
 * purely by {@code nonWhitespaceChars} in {@link PageExtractionDecider}.
 *
 * <p>Replacement characters (U+FFFD) count toward {@code replacementCharRatio} and are excluded
 * from the printable count.
 */
public record PageTextStats(int nonWhitespaceChars, double printableRatio, double replacementCharRatio) {

    private static final int REPLACEMENT = '�';

    public static PageTextStats from(String text) {
        int nonWhitespace = 0;
        int printable = 0;
        int replacement = 0;
        if (text != null) {
            Iterable<Integer> codePoints = () -> text.codePoints().iterator();
            for (int cp : codePoints) {
                if (Character.isWhitespace(cp)) {
                    continue;
                }
                nonWhitespace++;
                if (cp == REPLACEMENT) {
                    replacement++;
                } else if (Character.isDefined(cp) && !Character.isISOControl(cp)) {
                    printable++;
                }
            }
        }
        double printRatio = nonWhitespace == 0 ? 1.0 : (double) printable / nonWhitespace;
        double replRatio = nonWhitespace == 0 ? 0.0 : (double) replacement / nonWhitespace;
        return new PageTextStats(nonWhitespace, printRatio, replRatio);
    }
}
