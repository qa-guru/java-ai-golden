package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseComparison(
        String caseId,
        CaseRegression regression,
        Rate baselineSuccess,
        Rate candidateSuccess
) {
}
