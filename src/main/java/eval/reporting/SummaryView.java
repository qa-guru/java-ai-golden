package eval.reporting;

import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.QualityGateResult;
import eval.domain.Rate;

public record SummaryView(
        String runId,
        String timestamp,
        String model,
        String judgeModel,
        String datasetVersion,
        String gitCommit,
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
        QualityGateResult qualityGate,
        long durationMs
) {
    public static SummaryView of(EvalRun run) {
        return new SummaryView(
                run.runId(),
                run.timestamp(),
                run.model(),
                run.judgeModel(),
                run.datasetVersion(),
                run.gitCommit(),
                run.casesTotal(),
                run.casesPassed(),
                run.casesFailed(),
                run.casesSkipped(),
                run.casesError(),
                run.attemptsTotal(),
                run.attemptsPassed(),
                run.attemptsFailed(),
                run.attemptsError(),
                run.metrics(),
                run.qualityGate(),
                run.durationMs());
    }

    public static String formatRate(Rate rate) {
        if (rate == null || !rate.defined()) {
            return "n/a";
        }
        return rate.asPercent() + " (" + rate.asFraction() + ")";
    }
}
