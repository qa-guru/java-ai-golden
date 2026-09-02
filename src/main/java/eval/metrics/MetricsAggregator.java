package eval.metrics;

import eval.domain.AttemptResult;
import eval.domain.CaseKind;
import eval.domain.CaseResult;
import eval.domain.ContractResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalStatus;
import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;
import eval.domain.LatencyStats;
import eval.domain.MetricWeights;
import eval.domain.Rate;
import eval.domain.RetrievalResult;
import eval.domain.TokenUsage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregation is {@code totalHits / totalAttempts}, never the unweighted mean of per-case rates.
 * Slice keys come from case {@link eval.domain.CaseKind} metadata — the engine does not hardcode a category list.
 */
public final class MetricsAggregator {

    private MetricsAggregator() {
    }

    public static EvalMetrics aggregate(List<CaseResult> cases, MetricWeights weights) {
        int overallPass = 0;
        int overallTotal = 0;
        int contractPass = 0;
        int contractTotal = 0;
        int judgeAccept = 0;
        int judgeTotal = 0;
        int retrievalPass = 0;
        int retrievalTotal = 0;
        int negativePass = 0;
        int negativeTotal = 0;
        int hallucinationFail = 0;
        int hallucinationTotal = 0;
        int refusalPass = 0;
        int refusalTotal = 0;
        int layerPass = 0;
        int layerTotal = 0;
        int ragPass = 0;
        int ragTotal = 0;
        int unstable = 0;
        int stabilityDenom = 0;
        List<Long> latencies = new ArrayList<>();
        TokenUsage tokens = TokenUsage.unknown();
        Map<String, int[]> sliceAcc = new LinkedHashMap<>();

        if (cases == null) {
            cases = List.of();
        }

        for (CaseResult cse : cases) {
            boolean negative = hasKind(cse, CaseKind.NEGATIVE);
            boolean hallucination = hasKind(cse, CaseKind.HALLUCINATION);
            boolean refusal = hasKind(cse, CaseKind.REFUSAL);
            boolean layer = hasKind(cse, CaseKind.LAYER);
            boolean rag = hasKind(cse, CaseKind.RAG);

            if (cse.retrieval() != null && cse.retrieval().applicable()) {
                retrievalTotal++;
                if (cse.retrieval().passed()) {
                    retrievalPass++;
                }
            }

            if (cse.successRate() != null && cse.successRate().total() >= 2) {
                stabilityDenom++;
                if (cse.unstable()) {
                    unstable++;
                }
            }

            int caseQuality = 0;
            int casePass = 0;
            for (AttemptResult attempt : cse.attempts()) {
                if (attempt.durationMs() > 0) {
                    latencies.add(attempt.durationMs());
                }
                tokens = tokens.plus(attempt.tokens());
                if (!attempt.quality()) {
                    continue;
                }
                caseQuality++;
                overallTotal++;
                if (attempt.status() == EvalStatus.PASS) {
                    overallPass++;
                    casePass++;
                }
                ContractResult contract = attempt.contract();
                if (contract != null) {
                    contractTotal++;
                    if (contract.passed()) {
                        contractPass++;
                    }
                    if (contract.layerChecked()) {
                        layerTotal++;
                        if (contract.layerOk()) {
                            layerPass++;
                        }
                    }
                    if (contract.ragChecked()) {
                        ragTotal++;
                        if (contract.ragOk()) {
                            ragPass++;
                        }
                    }
                    if (refusal && contract.refuseChecked()) {
                        refusalTotal++;
                        if (contract.refuseOk()) {
                            refusalPass++;
                        }
                    }
                }
                JudgeResult judge = attempt.judge();
                if (judge != null && judge.decision() != JudgeDecision.PENDING) {
                    judgeTotal++;
                    if (judge.accepted()) {
                        judgeAccept++;
                    }
                }
                if (negative) {
                    negativeTotal++;
                    if (attempt.status() == EvalStatus.PASS) {
                        negativePass++;
                    }
                }
                if (hallucination) {
                    hallucinationTotal++;
                    if (attempt.status() != EvalStatus.PASS) {
                        hallucinationFail++;
                    }
                }
            }
            if (caseQuality > 0 && cse.kinds() != null) {
                for (CaseKind kind : cse.kinds()) {
                    String key = kind.name().toLowerCase(Locale.ROOT);
                    int[] acc = sliceAcc.computeIfAbsent(key, k -> new int[2]);
                    acc[0] += casePass;
                    acc[1] += caseQuality;
                }
            }
        }

        Rate overall = Rate.of(overallPass, overallTotal);
        Rate contract = Rate.of(contractPass, contractTotal);
        Rate judge = Rate.of(judgeAccept, judgeTotal);
        Rate retrieval = Rate.of(retrievalPass, retrievalTotal);
        Rate negative = Rate.of(negativePass, negativeTotal);
        Rate hallucination = Rate.of(hallucinationFail, hallucinationTotal);
        Rate refusal = Rate.of(refusalPass, refusalTotal);
        Rate layerRate = Rate.of(layerPass, layerTotal);
        Rate ragRate = Rate.of(ragPass, ragTotal);
        Rate unstableRate = Rate.of(unstable, stabilityDenom);

        Map<String, Rate> slices = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : sliceAcc.entrySet()) {
            slices.put(e.getKey(), Rate.of(e.getValue()[0], e.getValue()[1]));
        }

        Weighted weighted = weightedScore(
                weights == null ? MetricWeights.equal() : weights,
                contract, judge, retrieval, negative, hallucination, refusal, layerRate, ragRate);

        return new EvalMetrics(
                overall,
                contract,
                judge,
                retrieval,
                negative,
                hallucination,
                refusal,
                layerRate,
                ragRate,
                weighted.score,
                weighted.formula,
                LatencyStats.of(latencies),
                tokens,
                unstableRate,
                slices);
    }

    private static boolean hasKind(CaseResult cse, CaseKind kind) {
        return cse.kinds() != null && cse.kinds().contains(kind);
    }

    static Weighted weightedScore(
            MetricWeights w,
            Rate contract,
            Rate judge,
            Rate retrieval,
            Rate negative,
            Rate hallucination,
            Rate refusal,
            Rate layer,
            Rate rag) {
        record Term(String name, double weight, Rate rate, boolean invert) {
        }
        List<Term> terms = List.of(
                new Term("contract", w.contract(), contract, false),
                new Term("judge", w.judge(), judge, false),
                new Term("retrieval", w.retrieval(), retrieval, false),
                new Term("negative", w.negative(), negative, false),
                new Term("hallucinationInverse", w.hallucinationInverse(), hallucination, true),
                new Term("refusal", w.refusal(), refusal, false),
                new Term("layer", w.layer(), layer, false),
                new Term("rag", w.rag(), rag, false));
        double num = 0;
        double den = 0;
        List<String> parts = new ArrayList<>();
        for (Term t : terms) {
            if (t.weight <= 0 || !t.rate.defined()) {
                continue;
            }
            double v = t.invert ? (1.0 - t.rate.value()) : t.rate.value();
            num += t.weight * v;
            den += t.weight;
            parts.add(String.format(Locale.ROOT, "%.2f*%s(%.3f)", t.weight, t.name, v));
        }
        if (den == 0) {
            return new Weighted(null, null);
        }
        double score = num / den;
        String formula = "(" + String.join(" + ", parts) + ") / " + String.format(Locale.ROOT, "%.2f", den);
        return new Weighted(score, formula);
    }

    record Weighted(Double score, String formula) {
    }
}
