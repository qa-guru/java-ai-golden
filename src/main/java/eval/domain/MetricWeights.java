package eval.domain;

/**
 * Weighted composite is secondary. Component rates must always be reported.
 * Hallucination uses {@code 1 - hallucinationRate} (lower hallucination is better).
 * Components with {@code total == 0} are dropped and remaining weights renormalized.
 */
public record MetricWeights(
        double contract,
        double judge,
        double retrieval,
        double negative,
        double hallucinationInverse,
        double refusal,
        double layer,
        double rag
) {
    public static MetricWeights equal() {
        return new MetricWeights(1, 1, 1, 1, 1, 1, 1, 1);
    }

    public static MetricWeights disabled() {
        return new MetricWeights(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
