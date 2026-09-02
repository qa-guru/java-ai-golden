package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        boolean hallucinationHit,
        List<Violation> taxonomy
) {
    public ContractResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        taxonomy = taxonomy == null ? List.of() : List.copyOf(taxonomy);
    }

    public ContractResult(
            boolean passed,
            List<String> violations,
            boolean refuseChecked,
            boolean refuseOk,
            boolean ragChecked,
            boolean ragOk,
            boolean layerChecked,
            boolean layerOk,
            boolean hallucinationHit) {
        this(
                passed,
                violations,
                refuseChecked,
                refuseOk,
                ragChecked,
                ragOk,
                layerChecked,
                layerOk,
                hallucinationHit,
                List.of());
    }

    public static ContractResult pass() {
        return new ContractResult(true, List.of(), false, false, false, false, false, false, false, List.of());
    }

    public static ContractResult fail(List<String> violations) {
        List<String> messages = violations == null ? List.of() : List.copyOf(violations);
        List<Violation> tax = new ArrayList<>();
        for (String v : messages) {
            tax.add(new Violation(null, ViolationCategory.CONTRACT, ViolationSeverity.HIGH, "ContractGrader", v));
        }
        return new ContractResult(false, messages, false, false, false, false, false, false, false, tax);
    }

    public boolean hasCritical() {
        for (Violation v : taxonomy) {
            if (v.critical()) {
                return true;
            }
        }
        return false;
    }
}
