package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.config.LlmProperties;
import com.scb.trade.lcdocchecker.config.SafetyPrompts;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.GoodsDescriptionVerdict;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * UCP 600 Art. 18(c) — goods-description correspondence assessed by an LLM (the case-study
 * requires checks via well-defined LLM prompts). The LLM returns a structured
 * {@link GoodsDescriptionVerdict}; any failure/timeout/null verdict degrades to UNABLE (routed
 * to a human) — never a silent pass or a fabricated fail.
 *
 * <p>Coexists with the deterministic {@link GoodsDescriptionCheck} (token Jaccard); only one is
 * active at a time via the {@code lcchecker.rules.enabled} allowlist (default: this one). The
 * two share the same {@code @Order(60)} slot but are mutually exclusive in configuration.
 *
 * <p><b>Implementation note</b>: {@code sanitize} and {@code runWithTimeout} are inlined
 * (duplicated from {@code InvoiceExtractionService}). A shared {@code LlmCallSupport} is
 * deliberately NOT extracted yet — the current LLM surface is only two sites, and per the
 * case-study's "do not over-engineer" guidance a shared abstraction is deferred until a third
 * LLM call site appears (rule of three).
 */
@Component
@Order(60)
public class LlmGoodsDescriptionCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "goods_description";
    static final String RULE = RuleReference.UCP_600_ART_18_C.ref();

    private final ChatClient chatClient;
    private final String promptTemplate;
    private final Duration timeout;

    public LlmGoodsDescriptionCheck(ChatClient chatClient,
                                    LlmProperties llmProperties,
                                    @Value("${lcchecker.llm.goods-description-prompt-path:classpath:prompts/goods-description-check-v1.st}")
                                    Resource promptResource) {
        this.chatClient = chatClient;
        this.timeout = llmProperties == null ? Duration.ofSeconds(10) : llmProperties.effectiveTimeout();
        try {
            this.promptTemplate = new String(promptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load goods-description-check prompt template", e);
        }
    }

    @Override
    public String checkId() {
        return "llm_goods_description_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (lc == null || isBlank(lc.goodsDescription())
                || invoice == null || isBlank(invoice.goodsDescription())) {
            return CheckResult.notApplicable(checkId(), "Goods description not present on LC and/or invoice.");
        }
        try {
            GoodsDescriptionVerdict verdict =
                    runWithTimeout(() -> callLlm(lc.goodsDescription(), invoice.goodsDescription()), timeout);
            if (verdict == null || verdict.corresponds() == null) {
                return CheckResult.unable(checkId(), "LLM returned no usable verdict.");
            }
            if (verdict.corresponds()) {
                return CheckResult.pass(checkId());
            }
            String reason = isBlank(verdict.reason())
                    ? "The goods description does not correspond with that in the LC."
                    : verdict.reason();
            return CheckResult.fail(checkId(), Discrepancy.of(
                    FIELD, lc.goodsDescription(), invoice.goodsDescription(), RULE, reason));
        } catch (Exception e) {
            return CheckResult.unable(checkId(), "LLM goods-description check failed: " + e.getMessage());
        }
    }

    /** Calls the LLM with the goods-description prompt. Protected to allow test injection. */
    protected GoodsDescriptionVerdict callLlm(String lcGoods, String invoiceGoods) {
        return chatClient.prompt()
                .system(s -> s.text(SafetyPrompts.UNTRUSTED_INPUT_SYSTEM))
                .user(u -> u.text(promptTemplate)
                        .param("lcGoods", sanitize(lcGoods))
                        .param("invoiceGoods", sanitize(invoiceGoods)))
                .call()
                .entity(GoodsDescriptionVerdict.class);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
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
                throw new IllegalStateException(
                        "LLM goods-description check timed out after " + timeout.toMillis() + "ms.", e);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
