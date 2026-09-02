package eval.execution;

import eval.dataset.DatasetIdentity;
import eval.domain.RunConfiguration;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Stable hash of the evaluation target: model + judge + dataset + pack + protocol.
 * Does not include runId, timestamp, duration, or output paths.
 */
public final class ConfigFingerprint {

    private ConfigFingerprint() {
    }

    public static String of(
            RunConfiguration configuration,
            String datasetVersion,
            String datasetHash,
            String packDatasetVersion,
            String experimentId,
            String gitCommit) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("artifactMode", "omitted");
        fields.put("datasetHash", nz(datasetHash));
        fields.put("datasetVersion", nz(datasetVersion));
        fields.put("experimentId", nz(experimentId));
        fields.put("gitCommit", nz(gitCommit));
        fields.put("includeRed", configuration == null ? "" : String.valueOf(configuration.includeRed()));
        fields.put("judgeEnabled", configuration == null ? "" : String.valueOf(configuration.judgeEnabled()));
        fields.put("judgeModel", configuration == null || !configuration.judgeEnabled()
                ? "off"
                : nz(configuration.judgeModel()));
        fields.put("mode", configuration == null ? "" : nz(configuration.mode()));
        fields.put("model", configuration == null ? "" : nz(configuration.model()));
        fields.put("packDatasetVersion", nz(packDatasetVersion));
        fields.put("provider", configuration == null ? "" : nz(configuration.provider()));
        fields.put("repetitions", configuration == null ? "" : String.valueOf(configuration.repetitions()));
        String canonical = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        return DatasetIdentity.sha256Utf8(canonical);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
