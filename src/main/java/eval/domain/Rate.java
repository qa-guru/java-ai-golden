package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import eval.metrics.WilsonInterval;

/**
 * {@code passed / total} (or {@code hits / total} for inverted rates such as hallucination).
 * Undefined when {@code total == 0}. {@code ci95} is always recomputed (Wilson); it cannot be forged in JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Rate(int hits, int total, ConfidenceInterval ci95) {
    public Rate {
        if (hits < 0 || total < 0 || hits > total) {
            throw new IllegalArgumentException("invalid rate " + hits + "/" + total);
        }
        ci95 = WilsonInterval.of(hits, total);
    }

    public Rate(int hits, int total) {
        this(hits, total, null);
    }

    public static Rate empty() {
        return new Rate(0, 0);
    }

    public static Rate of(int hits, int total) {
        return new Rate(hits, total);
    }

    @JsonIgnore
    public boolean defined() {
        return total > 0;
    }

    /** {@code hits/total}, or {@code NaN} if undefined. */
    public double value() {
        return total == 0 ? Double.NaN : (double) hits / (double) total;
    }

    public Rate plus(Rate other) {
        return new Rate(hits + other.hits, total + other.total);
    }

    public String asPercent() {
        if (!defined()) {
            return "n/a";
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", value() * 100.0);
    }

    public String asFraction() {
        return hits + " / " + total;
    }

    public String asPercentWithCi() {
        if (!defined()) {
            return "n/a";
        }
        if (ci95 == null) {
            return asPercent() + " (" + asFraction() + ")";
        }
        return asPercent() + " (" + asFraction() + ") 95% CI " + ci95.asPercentRange();
    }
}
