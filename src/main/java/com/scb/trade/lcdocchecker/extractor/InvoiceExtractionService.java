package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.LlmProperties;
import com.scb.trade.lcdocchecker.config.SafetyPrompts;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceExtractedData;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Spring AI extraction for commercial invoices: maps raw invoice text → a structured
 * {@link InvoiceFields} via the modern fluent ChatClient API with structured-output
 * conversion ({@code .entity(InvoiceExtractedData.class)} implicitly uses
 * BeanOutputConverter) at temperature 0 for determinism.
 *
 * <p>Failures (timeout, unparseable JSON) propagate as {@link DocumentExtractionException}.
 */
@Service
public class InvoiceExtractionService implements DocumentExtractor<InvoiceFields> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);

    private final ChatClient chatClient;
    private final String promptTemplate;
    private final Duration timeout;

    public InvoiceExtractionService(ChatClient chatClient,
                                    LlmProperties llmProperties,
                                    @Value("${lcchecker.llm.prompt-template-path:classpath:prompts/invoice-extraction-v1.st}")
                                    Resource promptResource) {
        this.chatClient = chatClient;
        this.timeout = llmProperties == null ? Duration.ofSeconds(10) : llmProperties.effectiveTimeout();
        try {
            this.promptTemplate = new String(promptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load invoice-extraction prompt template", e);
        }
    }

    @Override
    public DocumentType documentType() {
        return DocumentType.INVOICE;
    }

    @Override
    public InvoiceFields extract(String text) {
        long startNs = System.nanoTime();
        FlowLog.info(log, InvoiceExtractionService.class, "extract",
                "stage", "START", "inputChars", text == null ? 0 : text.length(), "timeoutMs", timeout.toMillis());
        Callable<InvoiceFields> task = () -> {
            String sanitizedText = sanitizeInvoiceText(text);
            String renderedUserPrompt = new PromptTemplate(promptTemplate)
                    .render(Map.of("text", sanitizedText));
            FlowLog.info(log, InvoiceExtractionService.class, "extract",
                    "stage", "LLM_REQUEST",
                    "\ndocumentType", DocumentType.INVOICE,
                    "\nsystemPrompt", FlowLog.prettyValue(
                            SafetyPrompts.UNTRUSTED_INPUT_SYSTEM, Integer.MAX_VALUE),
                    "\nuserPromptChars", renderedUserPrompt.length(),
                    "\nuserPrompt", FlowLog.prettyValue(renderedUserPrompt, Integer.MAX_VALUE));
            ResponseEntity<ChatResponse, InvoiceExtractedData> result = chatClient.prompt()
                    .system(SafetyPrompts.UNTRUSTED_INPUT_SYSTEM)
                    .user(renderedUserPrompt)
                    .call()
                    .responseEntity(InvoiceExtractedData.class);
            ChatResponse response = result.response();
            String responseContent = response == null || response.getResult() == null
                    ? ""
                    : response.getResult().getOutput().getText();
            FlowLog.info(log, InvoiceExtractionService.class, "extract",
                    "stage", "LLM_RESPONSE",
                    "\ndocumentType", DocumentType.INVOICE,
                    "\nresponseChars", responseContent == null ? 0 : responseContent.length(),
                    "\nresponse", FlowLog.prettyValue(responseContent, Integer.MAX_VALUE),
                    "\nmetadata", response == null ? null : response.getMetadata());
            return result.entity().toFields(text);
        };
        try {
            InvoiceFields fields = runWithTimeout(task, timeout);
            FlowLog.info(log, InvoiceExtractionService.class, "extract",
                    "stage", "END",
                    "result", "success",
                    "sellerName", fields.sellerName(),
                    "totalAmount", fields.totalAmount(),
                    "currency", fields.currency(),
                    "costMs", elapsedMs(startNs));
            return fields;
        } catch (Exception e) {
            FlowLog.warn(log, InvoiceExtractionService.class, "extract",
                    "stage", "ERROR",
                    "errorMessage", e.getMessage(),
                    "costMs", elapsedMs(startNs));
            throw new DocumentExtractionException(
                    "Invoice field extraction via LLM failed: " + e.getMessage(), e);
        }
    }

    private static Duration normalizeTimeout(Duration timeout) {
        return timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(10) : timeout;
    }

    private static String sanitizeInvoiceText(String invoiceText) {
        if (invoiceText == null || invoiceText.isBlank()) {
            return "";
        }
        return invoiceText
                .replaceAll("(?i)^(system|assistant|developer)\\s*:", "[$1 redacted]:")
                .replaceAll("(?i)ignore previous instructions", "[redacted instruction]")
                .replaceAll("(?i)disregard (all|any) prior instructions", "[redacted instruction]");
    }

    private static <T> T runWithTimeout(Callable<T> task, Duration timeout) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = executor.submit(task);
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new DocumentExtractionException("Invoice field extraction via LLM timed out after " + timeout.toMillis() + "ms.", e);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
