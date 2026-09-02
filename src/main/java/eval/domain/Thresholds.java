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
}
