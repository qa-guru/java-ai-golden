package eval.generation;

import eval.domain.JudgeDecision;
import eval.domain.Rate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agreement of repeated judge calls on the same candidate. Not run in the default pipeline.
 */
public final class JudgeConsistency {

    private JudgeConsistency() {
    }

    public static Rate stability(List<JudgeDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return Rate.empty();
        }
        Map<JudgeDecision, Integer> counts = new EnumMap<>(JudgeDecision.class);
        for (JudgeDecision d : decisions) {
            JudgeDecision key = d == null ? JudgeDecision.PENDING : d;
            counts.merge(key, 1, Integer::sum);
        }
        int max = 0;
        for (int c : counts.values()) {
            max = Math.max(max, c);
        }
        return Rate.of(max, decisions.size());
    }
}
