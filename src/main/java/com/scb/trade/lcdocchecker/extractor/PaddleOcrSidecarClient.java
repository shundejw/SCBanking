package com.scb.trade.lcdocchecker.extractor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.scb.trade.lcdocchecker.config.OcrProperties;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;

/**
 * OCR fallback client that talks to a PaddleOCR HTTP sidecar (see ocr_server/Dockerfile). Renders
 * each PDF page to a 300-DPI PNG, base64-encodes it, POSTs to the configured paddle URL and
 * concatenates recognised text fragments whose confidence meets the threshold.
 *
 * <p>Network/parse failures are thrown as {@link DocumentExtractionException} — never silent.
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
    public String extract(byte[] pdfBytes) {
        long startNs = System.nanoTime();
        String endpoint = props.paddle() == null ? null : props.paddle().url();
        if (endpoint == null || endpoint.isBlank()) {
            throw new DocumentExtractionException("OCR sidecar URL is not configured.");
        }
        try {
            String[] images = renderPagesToBase64(pdfBytes);
            FlowLog.info(log, PaddleOcrSidecarClient.class, "extract",
                    "stage", "START", "pages", images.length, "endpoint", maskEndpoint(endpoint));
            String body = buildRequestBody(images);
            String response = restClient.post()
                    .uri(endpoint)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String text = parseText(response);
            FlowLog.info(log, PaddleOcrSidecarClient.class, "extract",
                    "stage", "END",
                    "result", "success",
                    "pages", images.length,
                    "textChars", text.length(),
                    "costMs", elapsedMs(startNs));
            return text;
        } catch (DocumentExtractionException e) {
            throw e;
        } catch (Exception e) {
            FlowLog.warn(log, PaddleOcrSidecarClient.class, "extract",
                    "stage", "ERROR",
                    "errorMessage", e.getMessage(),
                    "costMs", elapsedMs(startNs));
            throw new DocumentExtractionException("OCR sidecar call failed: " + e.getMessage(), e);
        }
    }

    private String[] renderPagesToBase64(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            String[] out = new String[doc.getNumberOfPages()];
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 300);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                out[i] = Base64.getEncoder().encodeToString(baos.toByteArray());
            }
            return out;
        }
    }

    private String buildRequestBody(String[] images) throws Exception {
        StringBuilder sb = new StringBuilder("{\"images\":[");
        for (int i = 0; i < images.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(images[i].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Walk the JSON collecting every {@code text} whose sibling {@code confidence} passes the threshold. */
    private String parseText(String response) throws Exception {
        if (response == null || response.isBlank()) {
            return "";
        }
        JsonNode root = mapper.readTree(response);
        StringBuilder text = new StringBuilder();
        collect(root, text);
        return text.toString();
    }

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
