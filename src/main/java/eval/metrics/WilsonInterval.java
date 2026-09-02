package eval.metrics;

import eval.domain.ConfidenceInterval;

/**
 * Wilson score interval for a binomial proportion. Preferred over a normal approximation
 * for small golden sets (n=8, n=10, n=40).
 */
public final class WilsonInterval {

    private static final double Z_95 = 1.959963984540054;

    private WilsonInterval() {
    }

    public static ConfidenceInterval of(int hits, int total) {
        return of(hits, total, Z_95);
    }

    static ConfidenceInterval of(int hits, int total, double z) {
        if (total <= 0) {
            return null;
        }
        if (hits < 0 || hits > total) {
            throw new IllegalArgumentException("invalid " + hits + "/" + total);
        }
        double n = total;
        double phat = hits / n;
        double z2 = z * z;
        double denom = 1.0 + z2 / n;
        double center = phat + z2 / (2.0 * n);
        double spread = z * Math.sqrt((phat * (1.0 - phat) + z2 / (4.0 * n)) / n);
        double lower = Math.max(0.0, (center - spread) / denom);
        double upper = Math.min(1.0, (center + spread) / denom);
        if (lower > upper) {
            double tmp = lower;
            lower = upper;
            upper = tmp;
        }
        return new ConfidenceInterval(lower, upper, "wilson");
    }
}
