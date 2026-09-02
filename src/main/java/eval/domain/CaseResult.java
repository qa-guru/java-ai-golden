package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseResult(
        String caseId,
        EvalStatus status,
        Set<CaseKind> kinds,
        List<AttemptResult> attempts,
        ContractResult contract,
        JudgeResult judge,
        RetrievalResult retrieval,
        Rate successRate,
        List<String> errors,
        long durationMs,
        java.util.Map<String, String> metadata
) {
    public CaseResult {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId");
        }
        kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        errors = errors == null ? List.of() : List.copyOf(errors);
        metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        successRate = successRate == null ? Rate.empty() : successRate;
    }

    public int qualityAttempts() {
        int n = 0;
        for (AttemptResult a : attempts) {
            if (a.quality()) {
                n++;
            }
        }
        return n;
    }

    public int passedAttempts() {
        int n = 0;
        for (AttemptResult a : attempts) {
            if (a.status() == EvalStatus.PASS) {
                n++;
            }
        }
        return n;
    }
}
