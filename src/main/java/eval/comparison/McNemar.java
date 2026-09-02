package eval.comparison;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * McNemar-style paired binary analysis. Informational — never the sole quality gate.
 *
 * {@code n01} = baseline PASS, candidate FAIL (regressions).
 * {@code n10} = baseline FAIL, candidate PASS (improvements).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McNemar(
        int n01,
        int n10,
        int discordant,
        double chiSquare,
        Double twoSidedPValue,
        String note
) {
    public static McNemar of(int baselinePassCandidateFail, int baselineFailCandidatePass) {
        int n01 = Math.max(0, baselinePassCandidateFail);
        int n10 = Math.max(0, baselineFailCandidatePass);
        int n = n01 + n10;
        if (n == 0) {
            return new McNemar(n01, n10, 0, 0.0, null, "no discordant pairs");
        }
        double chi = (n01 - n10) * (double) (n01 - n10) / n;
        double p = exactTwoSided(n01, n);
        return new McNemar(
                n01,
                n10,
                n,
                chi,
                p,
                "exact binomial two-sided under H0 p=0.5; not a quality gate");
    }

    static double exactTwoSided(int k, int n) {
        double lower = binomialCdf(Math.min(k, n - k), n);
        double p = Math.min(1.0, 2.0 * lower);
        return p;
    }

    static double binomialCdf(int k, int n) {
        double sum = 0;
        for (int i = 0; i <= k; i++) {
            sum += binomialPmfm(i, n);
        }
        return sum;
    }

    static double binomialPmfm(int k, int n) {
        return binomialCoefficient(n, k) * Math.pow(0.5, n);
    }

    static double binomialCoefficient(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        k = Math.min(k, n - k);
        double c = 1;
        for (int i = 1; i <= k; i++) {
            c *= (n - k + i);
            c /= i;
        }
        return c;
    }
}
