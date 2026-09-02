package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GateRuleResult(
        String name,
        String detail,
        boolean passed,
        Double actual,
        Double threshold,
        String kind
) {
}
