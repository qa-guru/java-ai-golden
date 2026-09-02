package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import eval.comparison.McNemar;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComparisonResult(
        boolean valid,
        String invalidReason,
        String baselineRunId,
        String candidateRunId,
        String baselineModel,
        String candidateModel,
        String datasetVersion,
        List<String> configurationDifferences,
        List<MetricDelta> metrics,
        List<CaseComparison> cases,
        QualityGateResult qualityGate,
        int unchangedPass,
        int unchangedFail,
        int regressions,
        int improvements,
        McNemar mcnemar,
        String decision
) {
    public ComparisonResult {
        configurationDifferences = configurationDifferences == null ? List.of() : List.copyOf(configurationDifferences);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (decision == null || decision.isBlank()) {
            decision = !valid
                    ? "COMPARISON_INVALID"
                    : (qualityGate != null && !qualityGate.passed()
                            ? "CONFIRMED_REGRESSION"
                            : "NO_CONFIRMED_REGRESSION");
        }
    }

    public ComparisonResult(
            boolean valid,
            String invalidReason,
            String baselineRunId,
            String candidateRunId,
            String baselineModel,
            String candidateModel,
            String datasetVersion,
            List<String> configurationDifferences,
            List<MetricDelta> metrics,
            List<CaseComparison> cases,
            QualityGateResult qualityGate) {
        this(
                valid,
                invalidReason,
                baselineRunId,
                candidateRunId,
                baselineModel,
                candidateModel,
                datasetVersion,
                configurationDifferences,
                metrics,
                cases,
                qualityGate,
                count(cases, CaseRegression.UNCHANGED_PASS),
                count(cases, CaseRegression.UNCHANGED_FAIL),
                count(cases, CaseRegression.NEW_FAILURE),
                count(cases, CaseRegression.RECOVERED),
                null,
                null);
    }

    public static ComparisonResult invalid(String reason) {
        return new ComparisonResult(
                false, reason, null, null, null, null, null, List.of(), List.of(), List.of(), null,
                0, 0, 0, 0, null, "COMPARISON_INVALID");
    }

    public List<CaseComparison> newFailures() {
        return cases.stream().filter(c -> c.regression() == CaseRegression.NEW_FAILURE).toList();
    }

    public List<CaseComparison> recovered() {
        return cases.stream().filter(c -> c.regression() == CaseRegression.RECOVERED).toList();
    }

    private static int count(List<CaseComparison> cases, CaseRegression want) {
        if (cases == null) {
            return 0;
        }
        int n = 0;
        for (CaseComparison c : cases) {
            if (c.regression() == want) {
                n++;
            }
        }
        return n;
    }
}
