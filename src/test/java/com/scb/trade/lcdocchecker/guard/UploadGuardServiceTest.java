package com.scb.trade.lcdocchecker.guard;

import com.scb.trade.lcdocchecker.config.UploadProperties;
import com.scb.trade.lcdocchecker.exception.UploadRejectedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadGuardServiceTest {

    private final UploadGuardService guard =
            new UploadGuardService(new UploadProperties(20, 10, "25504446", 50000));

    @Test
    void acceptsValidPdfHeader() {
        byte[] pdf = "25504446-not-used".getBytes();
        pdf[0] = 0x25; pdf[1] = 0x50; pdf[2] = 0x44; pdf[3] = 0x46; // %PDF
        assertDoesNotThrow(() -> guard.validatePdf(pdf));
    }

    @Test
    void rejectsNonPdfMagicBytes() {
        assertThrows(UploadRejectedException.class, () -> guard.validatePdf("plain text".getBytes()));
    }

    @Test
    void rejectsEmptyPdf() {
        assertThrows(UploadRejectedException.class, () -> guard.validatePdf(new byte[0]));
    }

    @Test
    void rejectsOversizedPdf() {
        byte[] pdf = new byte[(int) (21L * 1024 * 1024)];
        pdf[0] = 0x25; pdf[1] = 0x50; pdf[2] = 0x44; pdf[3] = 0x46;
        assertThrows(UploadRejectedException.class, () -> guard.validatePdf(pdf));
    }

    @Test
    void rejectsBlankLcText() {
        assertThrows(UploadRejectedException.class, () -> guard.validateLcText("   "));
    }

    @Test
    void enforcesPageCount() {
        assertThrows(UploadRejectedException.class, () -> guard.enforcePageCount(11));
        assertDoesNotThrow(() -> guard.enforcePageCount(10));
    }
}
