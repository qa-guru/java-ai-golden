package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QualityGateResult(
        boolean passed,
        String verdict,
        List<GateRuleResult> rules
) {
    public QualityGateResult {
        verdict = verdict == null ? (passed ? "PASS" : "FAIL") : verdict;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static QualityGateResult pass(List<GateRuleResult> rules) {
        return new QualityGateResult(true, "PASS", rules);
    }

    public static QualityGateResult fail(List<GateRuleResult> rules) {
        return new QualityGateResult(false, "FAIL", rules);
    }

    public static QualityGateResult skipped(String why) {
        return new QualityGateResult(true, "SKIPPED", List.of(new GateRuleResult("gate", why, true, null, null, null)));
    }
}
