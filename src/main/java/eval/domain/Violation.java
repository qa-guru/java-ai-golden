package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Violation(
        String caseId,
        ViolationCategory category,
        ViolationSeverity severity,
        String grader,
        String reason
) {
    public Violation {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("violation reason");
        }
        category = category == null ? ViolationCategory.OTHER : category;
        severity = severity == null ? ViolationSeverity.HIGH : severity;
        grader = grader == null || grader.isBlank() ? "ContractGrader" : grader;
    }

    public boolean critical() {
        return severity == ViolationSeverity.CRITICAL;
    }
}
