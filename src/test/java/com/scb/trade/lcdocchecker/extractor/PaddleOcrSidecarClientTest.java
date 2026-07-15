package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.OcrProperties;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PaddleOcrSidecarClient}'s response parsing against the real PaddleOCR
 * schema ({@code {"results":[[ {text, confidence}, ... ], ...]}}). No network: parsePages is
 * exercised directly with fixture JSON. HTTP behaviour is covered by the integration test.
 */
class PaddleOcrSidecarClientTest {

    private static final OcrProperties PROPS = new OcrProperties(
            0.5, 100, "http://localhost:8000/api/v1/ocr", Duration.ofSeconds(30),
            new OcrProperties.Paddle("http://localhost:8866/predict/ocr_system"));

    private final PaddleOcrSidecarClient client = new PaddleOcrSidecarClient(PROPS);

    private static OcrPageRequest req(int pageNumber) {
        return new OcrPageRequest(pageNumber, new byte[]{1});
    }

    @Test
    void parsesResultsArray_oneElementPerImage() throws Exception {
        String response = """
                {"results":[
                  [{"text":"INVOICE","confidence":0.99,"text_region":[[1,2],[3,4]]},
                   {"text":"USD 100","confidence":0.95}],
                  [{"text":"page two","confidence":0.90}]
                ]}""";

        List<OcrPageResult> out = client.parsePages(response, List.of(req(1), req(2)));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).pageNumber()).isEqualTo(1);
        assertThat(out.get(0).text()).contains("INVOICE", "USD 100");
        assertThat(out.get(1).pageNumber()).isEqualTo(2);
        assertThat(out.get(1).text()).contains("page two");
    }

    @Test
    void lowConfidenceDetectionsFiltered() throws Exception {
        String response = """
                {"results":[[{"text":"keep","confidence":0.9},{"text":"drop","confidence":0.1}]]}""";

        List<OcrPageResult> out = client.parsePages(response, List.of(req(1)));

        assertThat(out.get(0).text()).contains("keep").doesNotContain("drop");
    }

    @Test
    void countMismatch_failsLoudly() {
        String response = """
                {"results":[[{"text":"only one page","confidence":0.9}]]}""";

        assertThatThrownBy(() -> client.parsePages(response, List.of(req(1), req(2))))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("refusing to map pages");
    }

    @Test
    void missingResultsArray_failsLoudly() {
        assertThatThrownBy(() -> client.parsePages("{\"message\":\"ok\"}", List.of(req(1))))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("'results'");
    }

    @Test
    void blankResponse_failsLoudly() {
        assertThatThrownBy(() -> client.parsePages("   ", List.of(req(1))))
                .isInstanceOf(DocumentExtractionException.class);
    }
}
