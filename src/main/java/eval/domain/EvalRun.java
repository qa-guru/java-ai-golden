package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvalRun(
        String runId,
        String timestamp,
        String model,
        String judgeModel,
        String datasetVersion,
        String packDatasetVersion,
        String datasetHash,
        String packHash,
        String gitCommit,
        String experimentId,
        String configFingerprint,
        RunConfiguration configuration,
        int casesTotal,
        int casesPassed,
        int casesFailed,
        int casesSkipped,
        int casesError,
        int attemptsTotal,
        int attemptsPassed,
        int attemptsFailed,
        int attemptsSkipped,
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
        datasetHash = blankToNull(datasetHash);
        packHash = blankToNull(packHash);
        experimentId = blankToNull(experimentId);
        configFingerprint = blankToNull(configFingerprint);
    }

    /**
     * Legacy positional factory used by tests. New identity fields default to null/0.
     */
    public static EvalRun of(
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
            QualityGateResult qualityGate) {
        return new EvalRun(
                runId,
                timestamp,
                model,
                judgeModel,
                datasetVersion,
                packDatasetVersion,
                null,
                null,
                gitCommit,
                null,
                null,
                configuration,
                casesTotal,
                casesPassed,
                casesFailed,
                casesSkipped,
                casesError,
                attemptsTotal,
                attemptsPassed,
                attemptsFailed,
                0,
                attemptsError,
                metrics,
                cases,
                durationMs,
                qualityGate);
    }

    public EvalRun withQualityGate(QualityGateResult gate) {
        return new EvalRun(
                runId,
                timestamp,
                model,
                judgeModel,
                datasetVersion,
                packDatasetVersion,
                datasetHash,
                packHash,
                gitCommit,
                experimentId,
                configFingerprint,
                configuration,
                casesTotal,
                casesPassed,
                casesFailed,
                casesSkipped,
                casesError,
                attemptsTotal,
                attemptsPassed,
                attemptsFailed,
                attemptsSkipped,
                attemptsError,
                metrics,
                cases,
                durationMs,
                gate);
    }

    public double overallScore() {
        return metrics == null ? Double.NaN : metrics.overallPassRate().value();
    }

    public int casesExecuted() {
        return casesTotal - casesSkipped;
    }

    public Rate coverage() {
        return Rate.of(Math.max(0, casesExecuted()), Math.max(0, casesTotal));
    }

    public int qualityAttempts() {
        return attemptsPassed + attemptsFailed;
    }

    public boolean hasNoQualityAttempts() {
        return qualityAttempts() == 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
