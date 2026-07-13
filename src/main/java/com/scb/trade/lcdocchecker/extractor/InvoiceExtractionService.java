package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.domain.InvoiceExtractedData;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Spring AI extraction: maps raw invoice text to a structured {@link InvoiceExtractedData}
 * using the modern fluent ChatClient API with structured-output conversion
 * ({@code .entity(InvoiceExtractedData.class)} implicitly uses BeanOutputConverter) at
 * {@code temperature 0} for determinism.
 *
 * <p>Failures (timeout, unparseable JSON) propagate as {@link DocumentExtractionException}.
 */
@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);

    private final ChatClient chatClient;
    private final String promptTemplate;

    public InvoiceExtractionService(ChatClient chatClient,
                                    @Value("${lcchecker.llm.prompt-template-path:classpath:prompts/invoice-extraction-v1.st}")
                                    Resource promptResource) {
        this.chatClient = chatClient;
        try {
            this.promptTemplate = new String(promptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load invoice-extraction prompt template", e);
        }
    }

    public InvoiceExtractedData extract(String invoiceText) {
        long startNs = System.nanoTime();
        FlowLog.info(log, InvoiceExtractionService.class, "extract",
                "stage", "START", "inputChars", invoiceText == null ? 0 : invoiceText.length());
        try {
            InvoiceExtractedData data = chatClient.prompt()
                    .user(u -> u.text(promptTemplate).param("text", invoiceText))
                    .call()
                    .entity(InvoiceExtractedData.class);
            FlowLog.info(log, InvoiceExtractionService.class, "extract",
                    "stage", "END",
                    "result", "success",
                    "sellerName", data.sellerName(),
                    "totalAmount", data.totalAmount(),
                    "currency", data.currency(),
                    "costMs", elapsedMs(startNs));
            return data;
        } catch (Exception e) {
            FlowLog.warn(log, InvoiceExtractionService.class, "extract",
                    "stage", "ERROR",
                    "errorMessage", e.getMessage(),
                    "costMs", elapsedMs(startNs));
            throw new DocumentExtractionException(
                    "Invoice field extraction via LLM failed: " + e.getMessage(), e);
        }
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
