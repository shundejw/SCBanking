package com.scb.trade.lcdocchecker.guard;

import com.scb.trade.lcdocchecker.config.UploadProperties;
import com.scb.trade.lcdocchecker.exception.UploadRejectedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Validates raw upload bytes against size limits and PDF magic bytes, and enforces an
 * MT700 text length cap. Guards run before any parsing/extraction.
 */
@Service
public class UploadGuardService {

    private final UploadProperties props;

    public UploadGuardService(UploadProperties props) {
        this.props = props;
    }

    /** Validate an invoice PDF byte payload: magic bytes + size (page count is enforced by the extractor). */
    public void validatePdf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new UploadRejectedException("Uploaded invoice file is empty.");
        }
        long maxBytes = props.maxFileSizeMb() * 1024L * 1024L;
        if (bytes.length > maxBytes) {
            throw new UploadRejectedException(
                    "Uploaded invoice file exceeds the " + props.maxFileSizeMb() + "MB size limit.");
        }
        byte[] magic = props.magicBytes();
        if (magic.length > 0 && !startsWith(bytes, magic)) {
            throw new UploadRejectedException("Uploaded invoice file is not a valid PDF (magic bytes mismatch).");
        }
    }

    /** Enforce the MT700 plain-text length cap. */
    public void validateLcText(String lcText) {
        if (lcText == null || lcText.isBlank()) {
            throw new UploadRejectedException("LC MT700 text is empty.");
        }
        int cap = props.maxLcTextLengthChars() > 0 ? props.maxLcTextLengthChars() : 50_000;
        if (lcText.length() > cap) {
            throw new UploadRejectedException(
                    "LC MT700 text exceeds the " + cap + "-character limit.");
        }
    }

    /** Enforce the page-count cap (called by the extractor once PDFBox has opened the doc). */
    public void enforcePageCount(int pageCount) {
        if (pageCount > props.maxPagesAllowed()) {
            throw new UploadRejectedException(
                    "Invoice PDF exceeds the " + props.maxPagesAllowed() + "-page limit (" + pageCount + " pages).");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
