package com.scb.trade.lcdocchecker.extractor;

import java.util.List;

/**
 * Abstraction over the OCR sidecar. Accepts a batch of pre-rendered page images and returns one
 * result per page, keyed by page number. Implementations must NOT silently return fewer results
 * than requested — throw, so the caller degrades loudly (no silent page loss).
 *
 * <p>Internal batching/chunking is intentionally absent for the MVP: the number of OCR pages per
 * invoice document is expected to be small and is not a proven bottleneck, so a single batch call
 * suffices. The contract is the evolvable seam if per-request limits ever motivate chunking.
 */
public interface OcrGateway {

    /** OCR the supplied page images and return one result per page, preserving page numbers. */
    List<OcrPageResult> extractPages(List<OcrPageRequest> pages);
}
