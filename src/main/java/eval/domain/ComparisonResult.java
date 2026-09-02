package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        QualityGateResult qualityGate
) {
    public ComparisonResult {
        configurationDifferences = configurationDifferences == null ? List.of() : List.copyOf(configurationDifferences);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public static ComparisonResult invalid(String reason) {
        return new ComparisonResult(
                false, reason, null, null, null, null, null, List.of(), List.of(), List.of(), null);
    }

    public List<CaseComparison> newFailures() {
        return cases.stream().filter(c -> c.regression() == CaseRegression.NEW_FAILURE).toList();
    }

    public List<CaseComparison> recovered() {
        return cases.stream().filter(c -> c.regression() == CaseRegression.RECOVERED).toList();
    }
}
