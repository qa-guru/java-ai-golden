package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvalRun(
        String runId,
        String timestamp,
        String model,
        String judgeModel,
        String datasetVersion,
        String packDatasetVersion,
        String gitCommit,
        RunConfiguration configuration,
        int casesTotal,
        int casesPassed,
        int casesFailed,
        int casesSkipped,
        int casesError,
        int attemptsTotal,
        int attemptsPassed,
        int attemptsFailed,
        int attemptsError,
        EvalMetrics metrics,
        List<CaseResult> cases,
        long durationMs,
        QualityGateResult qualityGate
) {
    public EvalRun {
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId");
        }
    }

    public double overallScore() {
        return metrics == null ? Double.NaN : metrics.overallPassRate().value();
    }
}
