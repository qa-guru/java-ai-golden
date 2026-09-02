package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractResult(
        boolean passed,
        List<String> violations,
        boolean refuseChecked,
        boolean refuseOk,
        boolean ragChecked,
        boolean ragOk,
        boolean layerChecked,
        boolean layerOk,
        boolean hallucinationHit
) {
    public ContractResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static ContractResult pass() {
        return new ContractResult(true, List.of(), false, false, false, false, false, false, false);
    }

    public static ContractResult fail(List<String> violations) {
        return new ContractResult(false, violations, false, false, false, false, false, false, false);
    }
}
