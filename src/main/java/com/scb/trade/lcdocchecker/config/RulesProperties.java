package com.scb.trade.lcdocchecker.config;

import com.scb.trade.lcdocchecker.domain.DocumentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Active rule allowlist, bound from {@code lcchecker.rules}.
 *
 * <p>Two binding shapes are supported for migration safety:
 * <ul>
 *   <li><b>Legacy flat list</b> {@code lcchecker.rules.enabled: [...]} — treated as the
 *       {@link DocumentType#INVOICE} ruleset (the original single-document behavior).</li>
 *   <li><b>Per-type map</b> {@code lcchecker.rules.byType: {invoice: [...], packingList: [...]}}
 *       — maps each document type to its own checkId set.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "lcchecker.rules")
public record RulesProperties(
        List<String> enabled,
        Map<String, List<String>> byType) {

    /**
     * Resolve the active checkId allowlist for a document type.
     *
     * <p>Priority is fixed:
     * <ol>
     *   <li>{@code byType} non-empty → use {@code byType.get(type)} (empty list for an unmapped
     *       type means no checks);</li>
     *   <li>otherwise fall back to the legacy {@code enabled} list;</li>
     *   <li>{@code enabled == null} (absent) → returns {@code null}, meaning ALL registered
     *       checks run.</li>
     * </ol>
     */
    public Set<String> resolve(DocumentType type) {
        if (byType != null && !byType.isEmpty()) {
            List<String> ids = byType.getOrDefault(type.name().toLowerCase(), List.of());
            return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
        }
        return enabled == null ? null : Set.copyOf(enabled);
    }
}
