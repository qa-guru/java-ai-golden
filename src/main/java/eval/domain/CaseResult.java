package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashSet;
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
        java.util.Map<String, String> metadata,
        List<Violation> taxonomy,
        Rate judgeStability
) {
    public CaseResult {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId");
        }
        kinds = kinds == null || kinds.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(kinds));
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        errors = errors == null ? List.of() : List.copyOf(errors);
        metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        successRate = successRate == null ? Rate.empty() : successRate;
        taxonomy = taxonomy == null ? List.of() : List.copyOf(taxonomy);
    }

    public CaseResult(
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
            java.util.Map<String, String> metadata) {
        this(
                caseId,
                status,
                kinds,
                attempts,
                contract,
                judge,
                retrieval,
                successRate,
                errors,
                durationMs,
                metadata,
                contract == null ? List.of() : contract.taxonomy(),
                null);
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

    public boolean unstable() {
        Rate rate = successRate;
        return rate != null && rate.total() >= 2 && rate.hits() > 0 && rate.hits() < rate.total();
    }

    public boolean hasCriticalViolation() {
        for (Violation v : taxonomy) {
            if (v.critical()) {
                return true;
            }
        }
        return contract != null && contract.hasCritical();
    }
}
