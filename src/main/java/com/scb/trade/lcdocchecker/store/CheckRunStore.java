package com.scb.trade.lcdocchecker.store;

import com.scb.trade.lcdocchecker.domain.CheckReport;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store mapping a {@code runId} to its final report. (A case-study stand-in for a
 * durable store; suffices for the synchronous inspect endpoints.)
 */
@Component
public class CheckRunStore {

    private final Map<String, CheckReport> runs = new ConcurrentHashMap<>();

    public void put(String runId, CheckReport report) {
        runs.put(runId, report);
    }

    public Optional<CheckReport> get(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }
}
