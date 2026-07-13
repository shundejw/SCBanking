package com.scb.trade.lcdocchecker.store;

import tools.jackson.databind.ObjectMapper;
import com.scb.trade.lcdocchecker.config.ArtifactProperties;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists intermediate pipeline artifacts as JSON under
 * {@code <rootDir>/<runId>/<stage>.json} and keeps an in-memory copy for fast inspection.
 * Failures to write the artifact file never break a check run (best-effort).
 */
@Service
public class ArtifactStoreService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStoreService.class);

    private final ArtifactProperties props;
    private final ObjectMapper mapper;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public ArtifactStoreService(ArtifactProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public void save(String runId, String stage, Object payload) {
        String json;
        try {
            json = payload instanceof String s ? s : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            FlowLog.warn(log, ArtifactStoreService.class, "save",
                    "stage", "ERROR",
                    "runId", runId,
                    "step", stage,
                    "errorMessage", e.getMessage());
            return;
        }
        cache.computeIfAbsent(runId, k -> new ConcurrentHashMap<>()).put(stage, json);
        try {
            Path dir = Path.of(props.rootDir(), runId);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(stage + ".json"), json);
        } catch (IOException e) {
            FlowLog.warn(log, ArtifactStoreService.class, "save",
                    "stage", "ERROR",
                    "runId", runId,
                    "step", stage,
                    "errorMessage", e.getMessage());
        }
    }

    /** @return the raw JSON for a stage, or {@code null} if absent. */
    public String load(String runId, String stage) {
        Map<String, String> stages = cache.get(runId);
        return stages == null ? null : stages.get(stage);
    }

    public boolean exists(String runId) {
        return cache.containsKey(runId);
    }
}
