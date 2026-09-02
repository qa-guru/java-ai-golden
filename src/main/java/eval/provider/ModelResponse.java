package eval.provider;

import eval.domain.TokenUsage;

public record ModelResponse(
        String content,
        TokenUsage tokens,
        long durationMs
) {
    public ModelResponse {
        tokens = tokens == null ? TokenUsage.unknown() : tokens;
        if (content == null) {
            content = "";
        }
    }
}
