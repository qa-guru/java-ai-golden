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

    /**
     * Load-time contract: recorded aggregates must match {@code cases}.
     * Does not recompute or repair — a mismatch is invalid.
     */
    public void requireIntegrity() {
        int caseSum = casesPassed + casesFailed + casesSkipped + casesError;
        if (caseSum != casesTotal) {
            throw new IllegalArgumentException(
                    "INVALID RUN: casesPassed+Failed+Skipped+Error=" + caseSum
                            + " != casesTotal=" + casesTotal);
        }
        if (cases.size() != casesTotal) {
            throw new IllegalArgumentException(
                    "INVALID RUN: cases.size()=" + cases.size() + " != casesTotal=" + casesTotal);
        }
        int attemptSum = attemptsPassed + attemptsFailed + attemptsSkipped + attemptsError;
        if (attemptSum != attemptsTotal) {
            throw new IllegalArgumentException(
                    "INVALID RUN: attemptsPassed+Failed+Skipped+Error=" + attemptSum
                            + " != attemptsTotal=" + attemptsTotal);
        }
        if (metrics == null) {
            throw new IllegalArgumentException("INVALID RUN: metrics missing");
        }
        if (!cases.isEmpty()) {
            int qualityHits = 0;
            int qualityTotal = 0;
            for (CaseResult cse : cases) {
                for (AttemptResult a : cse.attempts()) {
                    if (a.quality()) {
                        qualityTotal++;
                        if (a.status() == EvalStatus.PASS) {
                            qualityHits++;
                        }
                    }
                }
            }
            Rate overall = metrics.overallPassRate();
            if (overall == null || overall.hits() != qualityHits || overall.total() != qualityTotal) {
                String recorded = overall == null ? "null" : overall.hits() + "/" + overall.total();
                throw new IllegalArgumentException(
                        "INVALID RUN: metrics.overallPassRate " + recorded
                                + " != quality attempts " + qualityHits + "/" + qualityTotal
                                + " derived from cases");
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
