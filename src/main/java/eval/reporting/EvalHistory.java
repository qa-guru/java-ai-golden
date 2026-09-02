package eval.reporting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.domain.EvalRun;
import eval.domain.QualityGateResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only JSONL. Not a database. One line per eval run.
 */
public final class EvalHistory {

    private EvalHistory() {
    }

    public static void append(Path file, EvalRun run) {
        if (file == null || run == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("timestamp", run.timestamp());
            line.put("runId", run.runId());
            line.put("gitCommit", run.gitCommit());
            line.put("experiment", run.experimentId());
            line.put("model", run.model());
            line.put("judgeModel", run.judgeModel());
            line.put("datasetVersion", run.datasetVersion());
            line.put("datasetHash", run.datasetHash());
            line.put("configFingerprint", run.configFingerprint());
            if (run.metrics() != null && run.metrics().overallPassRate() != null) {
                line.put("overallHits", run.metrics().overallPassRate().hits());
                line.put("overallTotal", run.metrics().overallPassRate().total());
                if (run.metrics().overallPassRate().defined()) {
                    line.put("overallPassRate", run.metrics().overallPassRate().value());
                }
                if (run.metrics().overallPassRate().ci95() != null) {
                    line.put("ci95Lower", run.metrics().overallPassRate().ci95().lower());
                    line.put("ci95Upper", run.metrics().overallPassRate().ci95().upper());
                }
            }
            QualityGateResult gate = run.qualityGate();
            line.put("qualityGate", gate == null ? "NOT_APPLIED" : gate.verdict());
            ObjectMapper compact = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
            String json = compact.writeValueAsString(line);
            Files.writeString(
                    file,
                    json + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
