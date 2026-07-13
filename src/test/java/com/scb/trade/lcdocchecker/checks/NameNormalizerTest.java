package com.scb.trade.lcdocchecker.checks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameNormalizerTest {

    @Test
    void identicalNamesMatch() {
        assertTrue(NameNormalizer.matches("XYZ EXPORT CO., LTD.", "XYZ EXPORT CO., LTD."));
        assertEquals(1.0, NameNormalizer.similarity("XYZ EXPORT CO., LTD.", "XYZ EXPORT CO., LTD."), 1e-9);
    }

    @Test
    void stripsDesignatorsSoPunctuationDoesNotBlockMatch() {
        // "CO., LTD." vs "CO" + "LTD" stripped on both sides → tokens {XYZ, EXPORT} match.
        assertTrue(NameNormalizer.matches("XYZ EXPORT CO., LTD.", "XYZ EXPORT CO LTD"));
    }

    @Test
    void differentSellersDoNotMatch() {
        assertFalse(NameNormalizer.matches("XYZ EXPORT CO., LTD.", "ACME SUGAR TRADING LTD"));
    }

    @Test
    void differentBuyersDoNotMatch() {
        assertFalse(NameNormalizer.matches("ABC IMPORTERS PTE LTD", "NORDIC TRADING ASIA PTE LTD"));
    }

    @Test
    void spoofingShortNameAgainstLongFraudulentNameFails() {
        // A substring/contains check would wrongly accept "ABC" inside "ABC...FRAUD"; Jaccard must not.
        assertFalse(NameNormalizer.matches("ABC LTD", "ABC FRAUDULENT FRONT COMPANY IMPORTERS PTE LTD"));
    }

    @Test
    void accentFolding() {
        assertTrue(NameNormalizer.matches("CAFE GMBH", "Café GmbH"));
    }
}
