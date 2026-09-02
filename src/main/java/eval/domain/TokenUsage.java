package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double estimatedCost
) {
    public static TokenUsage unknown() {
        return new TokenUsage(null, null, null, null);
    }

    public static TokenUsage of(Integer input, Integer output) {
        if (input == null && output == null) {
            return unknown();
        }
        Integer total = (input == null ? 0 : input) + (output == null ? 0 : output);
        if (input == null && output == null) {
            total = null;
        }
        return new TokenUsage(input, output, total, null);
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
                sum(inputTokens, other.inputTokens),
                sum(outputTokens, other.outputTokens),
                sum(totalTokens, other.totalTokens),
                sumCost(estimatedCost, other.estimatedCost));
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    private static Double sumCost(Double a, Double b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null || b == null) {
            return null;
        }
        return a + b;
    }
}
