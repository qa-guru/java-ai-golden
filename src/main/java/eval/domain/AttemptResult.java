package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptResult(
        int index,
        EvalStatus status,
        ContractResult contract,
        JudgeResult judge,
        String rawOutput,
        String judgeOutput,
        TokenUsage tokens,
        long durationMs,
        String errorKind,
        String errorMessage
) {
    public AttemptResult {
        if (status == null) {
            throw new IllegalArgumentException("attempt status");
        }
        tokens = tokens == null ? TokenUsage.unknown() : tokens;
    }

    public static AttemptResult skipped(int index, String why) {
        return new AttemptResult(
                index, EvalStatus.SKIPPED, null, null, null, null, TokenUsage.unknown(), 0, null, why);
    }

    public static AttemptResult error(int index, String kind, String message, long durationMs) {
        return new AttemptResult(
                index, EvalStatus.ERROR, null, null, null, null, TokenUsage.unknown(), durationMs, kind, message);
    }

    public boolean quality() {
        return status == EvalStatus.PASS || status == EvalStatus.FAIL;
    }
}
