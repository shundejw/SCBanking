package com.scb.trade.lcdocchecker.parser;

import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.exception.InvalidMt700Exception;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the programmatic MT700 parser. Uses the real fixture LC files under
 * {@code docs/test_fixtures/lc/} (surefire runs from the project root).
 */
class LcParserServiceTest {

    private static final Path LC_DIR = Path.of("docs/test_fixtures/lc");
    private final LcParserService parser = new LcParserService();

    private String read(String name) throws Exception {
        return Files.readString(LC_DIR.resolve(name));
    }

    @Test
    void parsesMainBaseline() throws Exception {
        LcTerms lc = parser.parse(read("SWIFT_MT700_Sample_Compliant.mt700"));

        assertEquals("LC202607120001", lc.lcNumber());
        assertEquals("IRREVOCABLE", lc.form());
        assertEquals("USD", lc.currency());
        assertEquals(new BigDecimal("57500.00"), lc.amount());
        assertEquals(new BigDecimal("5"), lc.tolerancePlus());
        assertEquals(new BigDecimal("5"), lc.toleranceMinus());
        assertFalse(lc.notExceeding());
        assertEquals(new BigDecimal("60375.00"), lc.amountCeiling());
        assertEquals(new BigDecimal("54625.00"), lc.amountFloor());

        assertEquals("ABC IMPORTERS PTE LTD", lc.applicantName());
        assertEquals("XYZ EXPORT CO., LTD.", lc.beneficiaryName());
        assertEquals("PORT OF SINGAPORE", lc.portOfLoading());
        assertEquals("PORT OF HAMBURG", lc.portOfDischarge());
        assertEquals("100 METRIC TONS OF REFINED SUGAR\nINCOTERMS 2020 CIF HAMBURG", lc.goodsDescription());
        assertEquals("METRIC TONS", lc.quantityUnit());
        assertEquals("WITHOUT", lc.confirmationInstructions());

        // main baseline :47A: requires the LC number; invoices in this baseline quote it.
        assertTrue(lc.requiresLcNumberOnInvoice());
        assertTrue(lc.requiresSignedInvoice());
    }

    @Test
    void parsesTagOnOwnLineParties() throws Exception {
        LcTerms lc = parser.parse(read("LC-DEMO-2026-0002-import-reference-fixed.mt700"));

        assertEquals("LCDEMO2026-0002", lc.lcNumber());
        // :50: tag is on its own line; name is the next non-blank line.
        assertEquals("EURO IMPORT DISTRIBUTION GMBH", lc.applicantName());
        assertEquals("GLOBAL TRADE LOGISTICS CORP.", lc.beneficiaryName());
        assertEquals(new BigDecimal("0"), lc.tolerancePlus());
        assertEquals(new BigDecimal("0"), lc.toleranceMinus());
        assertEquals("PORT OF NEW YORK, USA", lc.portOfLoading());

        // :46A: explicitly mandates the documentary credit number.
        assertTrue(lc.requiresLcNumberOnInvoice());
        assertTrue(lc.requiresSignedInvoice());
    }

    @Test
    void parsesParserCompatBaseline() throws Exception {
        LcTerms lc = parser.parse(read("MT700_Valid.mt700"));
        assertEquals("LC20260001", lc.lcNumber());
        assertEquals(new BigDecimal("100000.00"), lc.amount());
        assertEquals("SHANGHAI", lc.portOfLoading());
        assertEquals("SINGAPORE", lc.portOfDischarge());
        assertEquals("GOODS AS PER PURCHASE ORDER.", lc.goodsDescription());
        // No numeric quantity/unit pattern → blank, not null.
        assertEquals("", lc.quantityUnit());
    }

    @Test
    void invalidMt700Missing32bThrowsSpecificMessage() throws Exception {
        InvalidMt700Exception ex = assertThrows(InvalidMt700Exception.class,
                () -> parser.parse(read("MT700_Invalid_Missing32B.mt700")));
        // Exact fixture contract wording.
        assertEquals("mandatory field 32B (Currency Code, Amount) is missing.", ex.getMessage());
    }

    @Test
    void missingBlock4Throws() {
        InvalidMt700Exception ex = assertThrows(InvalidMt700Exception.class,
                () -> parser.parse("no swift structure here"));
        assertTrue(ex.getMessage().contains("Block 4"));
    }

    @Test
    void tag39bNotExceedingForcesZeroTolerancePlus() {
        String mt700 = """
                {1:F01BANKUS33XXXX0000000000}{2:I700SCBLSGSGXXXXN}{4:
                :27:1/1
                :40A:IRREVOCABLE
                :20:LC39BTEST
                :31C:260712
                :40E:UCP LATEST VERSION
                :31D:261031 SINGAPORE
                :50:ABC BUYER LTD
                :59:XYZ SELLER LTD
                :32B:USD10000,00
                :39A:10/10
                :39B:NOT EXCEEDING USD 10000
                :41A:SCBLSGSGXXX
                BY PAYMENT
                :49:WITHOUT
                -}""";
        LcTerms lc = parser.parse(mt700);
        assertTrue(lc.notExceeding());
        // :39A: said 10/10, but :39B: NOT EXCEEDING overrides tolerancePlus to 0 → ceiling == amount.
        assertEquals(BigDecimal.ZERO, lc.tolerancePlus());
        assertEquals(new BigDecimal("10000.00"), lc.amountCeiling());
    }

    @Test
    void tag39bMaximumAlsoForcesZeroTolerancePlus() {
        String mt700 = """
                {1:F01BANKUS33XXXX0000000000}{2:I700SCBLSGSGXXXXN}{4:
                :27:1/1
                :40A:IRREVOCABLE
                :20:LC39BMAX
                :31C:260712
                :40E:UCP LATEST VERSION
                :31D:261031 SINGAPORE
                :50:ABC BUYER LTD
                :59:XYZ SELLER LTD
                :32B:USD5000,00
                :39B:MAXIMUM USD 5000
                :41A:SCBLSGSGXXX
                BY PAYMENT
                :49:WITHOUT
                -}""";
        LcTerms lc = parser.parse(mt700);
        assertTrue(lc.notExceeding());
        assertEquals(new BigDecimal("5000.00"), lc.amountCeiling());
    }
}
