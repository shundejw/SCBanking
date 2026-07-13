package com.scb.trade.lcdocchecker.report;

import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ReportAssemblerService.class);

    public CheckReport assemble(List<CheckResult> results) {
        FlowLog.info(log, ReportAssemblerService.class, "assemble",
                "stage", "START", "checkCount", results.size());
        List<Discrepancy> discrepancies = results.stream()
                .filter(r -> r.status() == CheckStatus.FAIL)
                .map(CheckResult::discrepancy)
                .filter(Objects::nonNull)
                .toList();
        CheckReport report = CheckReport.of(discrepancies);
        FlowLog.info(log, ReportAssemblerService.class, "assemble",
                "stage", "END",
                "result", report.compliant() ? "COMPLIANT" : "NON_COMPLIANT",
                "discrepancies", discrepancies.size());
        return report;
    }
}
