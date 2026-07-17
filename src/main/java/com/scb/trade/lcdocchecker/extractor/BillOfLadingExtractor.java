package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.SafetyPrompts;
import com.scb.trade.lcdocchecker.domain.BillOfLading;
import com.scb.trade.lcdocchecker.domain.BillOfLadingExtractedData;
import com.scb.trade.lcdocchecker.domain.DocumentType;
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
import java.util.Map;

/**
 * Spring AI extraction for bills of lading: raw BoL text → {@link BillOfLading} via the fluent
 * ChatClient API with structured-output conversion ({@code .entity(BillOfLadingExtractedData.class)}),
 * temperature 0 for determinism, and the same prompt-injection guard as the invoice extractor.
 *
 * <p>This bean — together with the {@link DocumentType#BILL_OF_LADING} constant — is the FULL
 * extent of wiring a new document type at the extraction layer; {@code DocumentExtractorService}
 * collects it by type and dispatches automatically. (BoL uses the ChatClient's own HTTP timeout;
 * the invoice extractor's separate executor-timeout wrapper is invoice-specific.)
 */
@Service
public class BillOfLadingExtractor implements DocumentExtractor<BillOfLading> {

    private static final Logger log = LoggerFactory.getLogger(BillOfLadingExtractor.class);

    private final ChatClient chatClient;
    private final String promptTemplate;

    public BillOfLadingExtractor(ChatClient chatClient,
                                 @Value("${lcchecker.llm.bol-prompt-template-path:classpath:prompts/bill-of-lading-extraction-v1.st}")
                                 Resource promptResource) {
        this.chatClient = chatClient;
        try {
            this.promptTemplate = new String(promptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load bill-of-lading prompt template", e);
        }
    }

    @Override
    public DocumentType documentType() {
        return DocumentType.BILL_OF_LADING;
    }

    @Override
    public BillOfLading extract(String text) {
        long startNs = System.nanoTime();
        FlowLog.info(log, BillOfLadingExtractor.class, "extract",
                "stage", "START", "inputChars", text == null ? 0 : text.length());
        try {
            String sanitized = sanitize(text);
            String renderedUserPrompt = new PromptTemplate(promptTemplate)
                    .render(Map.of("text", sanitized));
            FlowLog.info(log, BillOfLadingExtractor.class, "extract",
                    "stage", "LLM_REQUEST",
                    "\ndocumentType", DocumentType.BILL_OF_LADING,
                    "\nsystemPrompt", FlowLog.prettyValue(
                            SafetyPrompts.UNTRUSTED_INPUT_SYSTEM, Integer.MAX_VALUE),
                    "\nuserPromptChars", renderedUserPrompt.length(),
                    "\nuserPrompt", FlowLog.prettyValue(renderedUserPrompt, Integer.MAX_VALUE));

            ResponseEntity<ChatResponse, BillOfLadingExtractedData> result = chatClient.prompt()
                    .system(SafetyPrompts.UNTRUSTED_INPUT_SYSTEM)
                    .user(renderedUserPrompt)
                    .call()
                    .responseEntity(BillOfLadingExtractedData.class);
            ChatResponse response = result.response();
            String responseContent = response == null || response.getResult() == null
                    ? ""
                    : response.getResult().getOutput().getText();
            FlowLog.info(log, BillOfLadingExtractor.class, "extract",
                    "stage", "LLM_RESPONSE",
                    "\ndocumentType", DocumentType.BILL_OF_LADING,
                    "\nresponseChars", responseContent == null ? 0 : responseContent.length(),
                    "\nresponse", FlowLog.prettyValue(responseContent, Integer.MAX_VALUE),
                    "\nmetadata", response == null ? null : response.getMetadata());

            BillOfLading bol = result.entity().toBillOfLading(text);
            FlowLog.info(log, BillOfLadingExtractor.class, "extract",
                    "stage", "END", "result", "success",
                    "\nblNumber", bol.blNumber(), "shipper", bol.shipper(),
                    "\ncostMs", elapsedMs(startNs));
            return bol;
        } catch (Exception e) {
            FlowLog.warn(log, BillOfLadingExtractor.class, "extract",
                    "stage", "ERROR", "errorMessage", e.getMessage(), "costMs", elapsedMs(startNs));
            throw new DocumentExtractionException("Bill of lading extraction via LLM failed: " + e.getMessage(), e);
        }
    }

    private static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("(?i)^(system|assistant|developer)\\s*:", "[$1 redacted]:")
                .replaceAll("(?i)ignore previous instructions", "[redacted instruction]")
                .replaceAll("(?i)disregard (all|any) prior instructions", "[redacted instruction]");
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
