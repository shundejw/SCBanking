package com.scb.trade.lcdocchecker.extractor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.scb.trade.lcdocchecker.config.OcrProperties;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

/**
 * OCR sidecar client talking to a PaddleOCR HTTP service. Receives pre-rendered page images
 * ({@link OcrPageRequest}), base64-encodes them, POSTs {@code {"images":[...]}} and returns one
 * recognised-text result per page. Page rendering lives in the caller, so this class has no PDF
 * dependency.
 *
 * <p>Response mapping expects PaddleOCR's {@code {"results":[[ ... ], ...]}} schema: the
 * {@code results} array holds one element per input image, in input order; each element is the
 * list of {@code {text, confidence}} detections for that page. A missing {@code results} array or
 * a count mismatch is a loud failure (no silent page loss / no ambiguous page mapping). The caller
 * additionally rejects blank text for a page that should contain content.
 *
 * <p>Network/parse failures throw {@link DocumentExtractionException} — never silent.
 */
@Component
public class PaddleOcrSidecarClient implements OcrGateway {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrSidecarClient.class);

    private final OcrProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public PaddleOcrSidecarClient(OcrProperties props) {
        this.props = props;
        Duration timeout = props.timeout() == null ? Duration.ofSeconds(30) : props.timeout();
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults().withTimeouts(timeout, timeout));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public List<OcrPageResult> extractPages(List<OcrPageRequest> pages) {
        long startNs = System.nanoTime();
        String endpoint = props.paddle() == null ? null : props.paddle().url();
        if (endpoint == null || endpoint.isBlank()) {
            throw new DocumentExtractionException("OCR sidecar URL is not configured.");
        }
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        try {
            List<String> images = encodeImages(pages);
            FlowLog.info(log, PaddleOcrSidecarClient.class, "extractPages",
                    "stage", "START", "pages", images.size(), "endpoint", maskEndpoint(endpoint));
            String body = buildRequestBody(images);
            String response = restClient.post()
                    .uri(endpoint)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            List<OcrPageResult> results = parsePages(response, pages);
            int chars = results.stream().mapToInt(r -> r.text().length()).sum();
            FlowLog.info(log, PaddleOcrSidecarClient.class, "extractPages",
                    "stage", "END",
                    "result", "success",
                    "pages", results.size(),
                    "textChars", chars,
                    "costMs", elapsedMs(startNs));
            return results;
        } catch (DocumentExtractionException e) {
            throw e;
        } catch (Exception e) {
            FlowLog.warn(log, PaddleOcrSidecarClient.class, "extractPages",
                    "stage", "ERROR",
                    "errorMessage", e.getMessage(),
                    "costMs", elapsedMs(startNs));
            throw new DocumentExtractionException("OCR sidecar call failed: " + e.getMessage(), e);
        }
    }

    private List<String> encodeImages(List<OcrPageRequest> pages) {
        List<String> out = new ArrayList<>(pages.size());
        for (OcrPageRequest p : pages) {
            out.add(Base64.getEncoder().encodeToString(p.imageBytes()));
        }
        return out;
    }

    private String buildRequestBody(List<String> images) {
        StringBuilder sb = new StringBuilder("{\"images\":[");
        for (int i = 0; i < images.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(images.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    List<OcrPageResult> parsePages(String response, List<OcrPageRequest> requests) throws Exception {
        if (response == null || response.isBlank()) {
            throw new DocumentExtractionException(
                    "OCR sidecar returned an empty response for " + requests.size() + " page(s).");
        }
        JsonNode results = mapper.readTree(response).path("results");
        if (!results.isArray()) {
            throw new DocumentExtractionException(
                    "OCR sidecar response had no 'results' array; cannot map results to pages.");
        }
        if (results.size() != requests.size()) {
            throw new DocumentExtractionException("OCR sidecar returned " + results.size()
                    + " result set(s) for " + requests.size() + " page(s); refusing to map pages ambiguously.");
        }
        List<OcrPageResult> out = new ArrayList<>(requests.size());
        for (int i = 0; i < results.size(); i++) {
            StringBuilder text = new StringBuilder();
            collect(results.get(i), text);
            out.add(new OcrPageResult(requests.get(i).pageNumber(), text.toString()));
        }
        return out;
    }

    /** Walk a JSON subtree collecting every {@code text} whose sibling {@code confidence} passes the threshold. */
    private void collect(JsonNode node, StringBuilder out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode textNode = node.get("text");
            if (textNode != null && textNode.isTextual()) {
                double conf = node.hasNonNull("confidence") ? node.get("confidence").asDouble() : 1.0;
                if (conf >= props.confidenceThreshold()) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(textNode.asText());
                }
            }
        }
        Iterator<JsonNode> it = node.iterator();
        while (it.hasNext()) {
            collect(it.next(), out);
        }
    }

    private static String maskEndpoint(String endpoint) {
        int slash = endpoint.lastIndexOf('/');
        return slash < 0 ? endpoint : "..." + endpoint.substring(slash);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
