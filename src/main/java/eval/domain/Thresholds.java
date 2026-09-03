package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Thresholds(
        Double overallPassRate,
        Double contractPassRate,
        Double judgeAcceptRate,
        Double retrievalPassRate,
        Double negativeCasePassRate,
        Double refusalAccuracy,
        Double layerAccuracy,
        Double ragAccuracy,
        Double hallucinationRate,
        Double allowedRegression
) {
    public static Thresholds none() {
        return new Thresholds(null, null, null, null, null, null, null, null, null, null);
    }

    /** Fixture eval: every recorded case must still pass. */
    public static Thresholds deterministicStrict() {
        return new Thresholds(1.0, 1.0, null, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, null);
    }

    /**
     * Live/model eval: no absolute mins (would paint 7b or hide a bad snapshot).
     * Delta vs live baseline only. {@code allowedRegression = 0}: any drop in a hard rate is a gate FAIL.
     * Do not set this to 1/N for the 5-attempt live smoke — that would let one case fail and still pass.
     */
    public static Thresholds liveDelta() {
        return new Thresholds(null, null, null, null, null, null, null, null, null, 0.0);
    }
}
