package eval.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvalMetrics(
        Rate overallPassRate,
        Rate contractPassRate,
        Rate judgeAcceptRate,
        Rate retrievalPassRate,
        Rate negativeCasePassRate,
        Rate hallucinationRate,
        Rate refusalAccuracy,
        Rate layerAccuracy,
        Rate ragAccuracy,
        Double weightedScore,
        String weightedScoreFormula,
        LatencyStats latency,
        TokenUsage tokens,
        Rate unstableCaseRate,
        Map<String, Rate> slices
) {
    public EvalMetrics {
        overallPassRate = overallPassRate == null ? Rate.empty() : overallPassRate;
        contractPassRate = contractPassRate == null ? Rate.empty() : contractPassRate;
        judgeAcceptRate = judgeAcceptRate == null ? Rate.empty() : judgeAcceptRate;
        retrievalPassRate = retrievalPassRate == null ? Rate.empty() : retrievalPassRate;
        negativeCasePassRate = negativeCasePassRate == null ? Rate.empty() : negativeCasePassRate;
        hallucinationRate = hallucinationRate == null ? Rate.empty() : hallucinationRate;
        refusalAccuracy = refusalAccuracy == null ? Rate.empty() : refusalAccuracy;
        layerAccuracy = layerAccuracy == null ? Rate.empty() : layerAccuracy;
        ragAccuracy = ragAccuracy == null ? Rate.empty() : ragAccuracy;
        latency = latency == null ? LatencyStats.empty() : latency;
        tokens = tokens == null ? TokenUsage.unknown() : tokens;
        unstableCaseRate = unstableCaseRate == null ? Rate.empty() : unstableCaseRate;
        slices = slices == null ? Map.of() : Map.copyOf(slices);
    }

    public EvalMetrics(
            Rate overallPassRate,
            Rate contractPassRate,
            Rate judgeAcceptRate,
            Rate retrievalPassRate,
            Rate negativeCasePassRate,
            Rate hallucinationRate,
            Rate refusalAccuracy,
            Rate layerAccuracy,
            Rate ragAccuracy,
            Double weightedScore,
            String weightedScoreFormula,
            LatencyStats latency,
            TokenUsage tokens) {
        this(
                overallPassRate,
                contractPassRate,
                judgeAcceptRate,
                retrievalPassRate,
                negativeCasePassRate,
                hallucinationRate,
                refusalAccuracy,
                layerAccuracy,
                ragAccuracy,
                weightedScore,
                weightedScoreFormula,
                latency,
                tokens,
                Rate.empty(),
                Map.of());
    }
}
