package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.domain.InvoiceExtractedData;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
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
        try {
            return chatClient.prompt()
                    .user(u -> u.text(promptTemplate).param("text", invoiceText))
                    .call()
                    .entity(InvoiceExtractedData.class);
        } catch (Exception e) {
            log.warn("Spring AI invoice extraction failed: {}", e.getMessage());
            throw new DocumentExtractionException(
                    "Invoice field extraction via LLM failed: " + e.getMessage(), e);
        }
    }
}
