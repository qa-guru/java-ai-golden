package eval.metrics;

import eval.domain.AttemptResult;
import eval.domain.CaseKind;
import eval.domain.CaseResult;
import eval.domain.ContractResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalStatus;
import eval.domain.MetricWeights;
import eval.domain.Rate;
import eval.domain.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Metrics aggregation")
class MetricsAggregatorTest {

    @Test
    @DisplayName("overall is totalPassed/totalAttempts, not the mean of per-case rates")
    void overallUsesAttemptCountsNotMeanOfRates() {
        CaseResult oneShot = caseWithAttempts("GEN-001", Set.of(CaseKind.GENERATION), List.of(pass(1)));
        List<AttemptResult> many = new java.util.ArrayList<>();
        many.add(pass(1));
        for (int i = 2; i <= 100; i++) {
            many.add(fail(i));
        }
        CaseResult longCase = caseWithAttempts("GEN-002", Set.of(CaseKind.GENERATION), many);
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(oneShot, longCase), MetricWeights.equal());
        assertEquals(2, metrics.overallPassRate().hits());
        assertEquals(101, metrics.overallPassRate().total());
        double meanOfRates = (1.0 + 0.01) / 2.0;
        assertTrue(Math.abs(metrics.overallPassRate().value() - meanOfRates) > 0.4);
        assertEquals(2.0 / 101.0, metrics.overallPassRate().value(), 1e-12);
    }

    @Test
    void skippedAttemptsAreNotPasses() {
        AttemptResult skip = AttemptResult.skipped(1, "red");
        CaseResult skipped = new CaseResult(
                "GEN-SKIP",
                EvalStatus.SKIPPED,
                Set.of(CaseKind.GENERATION),
                List.of(skip),
                null,
                null,
                eval.domain.RetrievalResult.notApplicable(),
                Rate.empty(),
                List.of(),
                0,
                Map.of());
        CaseResult passed = caseWithAttempts("GEN-OK", Set.of(CaseKind.GENERATION), List.of(pass(1)));
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(passed, skipped), MetricWeights.equal());
        assertEquals(1, metrics.overallPassRate().hits());
        assertEquals(1, metrics.overallPassRate().total());
        assertEquals(1.0, metrics.overallPassRate().value(), 1e-12);
    }

    @Test
    void emptyCasesDoNotYieldOneHundredPercent() {
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(), MetricWeights.equal());
        assertTrue(!metrics.overallPassRate().defined());
    }

    @Test
    void threeCasesFiveRepetitionsAreFifteenAttempts() {
        List<CaseResult> cases = List.of(
                caseWithAttempts("GEN-001", Set.of(CaseKind.GENERATION), five(5)),
                caseWithAttempts("GEN-002", Set.of(CaseKind.GENERATION), List.of(
                        pass(1), pass(2), pass(3), fail(4), fail(5))),
                caseWithAttempts("GEN-003", Set.of(CaseKind.GENERATION), five(5)));
        EvalMetrics metrics = MetricsAggregator.aggregate(cases, MetricWeights.equal());
        assertEquals(13, metrics.overallPassRate().hits());
        assertEquals(15, metrics.overallPassRate().total());
        assertEquals(13.0 / 15.0, metrics.overallPassRate().value(), 1e-12);
        assertEquals(1, metrics.unstableCaseRate().hits());
        assertEquals(3, metrics.unstableCaseRate().total());
        assertTrue(metrics.slices().containsKey("generation"));
        assertEquals(13, metrics.slices().get("generation").hits());
    }

    @Test
    void errorIsNotAPassOrAQualityFail() {
        CaseResult cse = caseWithAttempts(
                "GEN-ERR",
                Set.of(CaseKind.GENERATION),
                List.of(AttemptResult.error(1, "MODEL_UNAVAILABLE", "down", 5)));
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(cse), MetricWeights.equal());
        assertTrue(!metrics.overallPassRate().defined());
    }

    private static List<AttemptResult> five(int passed) {
        List<AttemptResult> out = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            out.add(i <= passed ? pass(i) : fail(i));
        }
        return out;
    }

    @Test
    void errorAttemptsAreExcludedFromPassRate() {
        CaseResult cse = caseWithAttempts(
                "GEN-003",
                Set.of(CaseKind.GENERATION),
                List.of(pass(1), AttemptResult.error(2, "MODEL_UNAVAILABLE", "down", 5)));
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(cse), MetricWeights.equal());
        assertEquals(1, metrics.overallPassRate().hits());
        assertEquals(1, metrics.overallPassRate().total());
    }

    @Test
    void hallucinationRateIsFailOverHallucinationAttempts() {
        CaseResult cse = caseWithAttempts(
                "hallucinate-error",
                Set.of(CaseKind.GENERATION, CaseKind.HALLUCINATION, CaseKind.NEGATIVE),
                List.of(pass(1), fail(2), fail(3), fail(4), fail(5)));
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(cse), MetricWeights.equal());
        assertEquals(4, metrics.hallucinationRate().hits());
        assertEquals(5, metrics.hallucinationRate().total());
    }

    @Test
    void undefinedComponentsAreDroppedFromWeightedScore() {
        CaseResult cse = caseWithAttempts(
                "login-401-api",
                Set.of(CaseKind.GENERATION, CaseKind.LAYER, CaseKind.RAG),
                List.of(pass(1)));
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(cse), MetricWeights.equal());
        assertTrue(metrics.weightedScore() != null);
        assertTrue(metrics.weightedScoreFormula().contains("contract"));
        assertTrue(!metrics.weightedScoreFormula().contains("judge") || metrics.judgeAcceptRate().defined());
    }

    private static CaseResult caseWithAttempts(String id, Set<CaseKind> kinds, List<AttemptResult> attempts) {
        int passed = 0;
        int quality = 0;
        for (AttemptResult a : attempts) {
            if (a.quality()) {
                quality++;
                if (a.status() == EvalStatus.PASS) {
                    passed++;
                }
            }
        }
        EvalStatus status = quality == 0 ? EvalStatus.ERROR : (passed == quality ? EvalStatus.PASS : EvalStatus.FAIL);
        return new CaseResult(
                id,
                status,
                kinds,
                attempts,
                attempts.getFirst().contract(),
                null,
                eval.domain.RetrievalResult.notApplicable(),
                Rate.of(passed, quality),
                List.of(),
                10,
                Map.of());
    }

    private static AttemptResult pass(int i) {
        return new AttemptResult(
                i, EvalStatus.PASS, ContractResult.pass(), null, "ok", null, TokenUsage.unknown(), 10, null, null);
    }

    private static AttemptResult fail(int i) {
        return new AttemptResult(
                i,
                EvalStatus.FAIL,
                ContractResult.fail(List.of("nope")),
                null,
                "bad",
                null,
                TokenUsage.unknown(),
                10,
                null,
                "nope");
    }
}
