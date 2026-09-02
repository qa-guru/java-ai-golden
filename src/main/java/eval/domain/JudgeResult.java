package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeResult(
        JudgeDecision decision,
        Double score,
        List<String> reasons,
        boolean schemaValid,
        String raw
) {
    public JudgeResult {
        if (decision == null) {
            decision = JudgeDecision.PENDING;
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static JudgeResult pending(String raw) {
        return new JudgeResult(JudgeDecision.PENDING, null, List.of(), false, raw);
    }

    public static JudgeResult invalidSchema(String raw, String why) {
        return new JudgeResult(JudgeDecision.PENDING, null, List.of(why), false, raw);
    }

    public boolean accepted() {
        return decision == JudgeDecision.ACCEPT;
    }

    public boolean rejected() {
        return decision == JudgeDecision.REJECT;
    }
}
