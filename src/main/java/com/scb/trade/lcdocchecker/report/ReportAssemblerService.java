package com.scb.trade.lcdocchecker.report;

import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Folds ordered {@link CheckResult}s into the final {@link CheckReport}.
 * Only {@link CheckStatus#FAIL} results become {@link Discrepancy}s (which drive
 * {@code compliant = false}). {@code UNABLE}/{@code PASS}/{@code NOT_APPLICABLE} do not
 * appear in the response (UNABLE is preserved in the {@code check_results} artifact).
 */
@Service
public class ReportAssemblerService {

    public CheckReport assemble(List<CheckResult> results) {
        List<Discrepancy> discrepancies = results.stream()
                .filter(r -> r.status() == CheckStatus.FAIL)
                .map(CheckResult::discrepancy)
                .filter(Objects::nonNull)
                .toList();
        return CheckReport.of(discrepancies);
    }
}
