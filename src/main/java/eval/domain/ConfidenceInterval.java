package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Inclusive 95% interval for a binomial rate. Bounds are in {@code [0, 1]}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfidenceInterval(double lower, double upper, String method) {
    public ConfidenceInterval {
        if (lower < 0 || upper > 1 || lower > upper + 1e-12) {
            throw new IllegalArgumentException("invalid interval [" + lower + ", " + upper + "]");
        }
        method = method == null || method.isBlank() ? "wilson" : method;
    }

    public String asPercentRange() {
        return String.format(java.util.Locale.ROOT, "[%.1f%%, %.1f%%]", lower * 100.0, upper * 100.0);
    }
}
