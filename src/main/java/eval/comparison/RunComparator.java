package eval.comparison;

import eval.domain.CaseComparison;
import eval.domain.CaseRegression;
import eval.domain.CaseResult;
import eval.domain.ComparisonResult;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.domain.MetricDelta;
import eval.domain.QualityGateResult;
import eval.domain.Rate;
import eval.domain.Thresholds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public final class RunComparator {

    private RunComparator() {
    }

    public static ComparisonResult compare(EvalRun baseline, EvalRun candidate) {
        return compare(baseline, candidate, null);
    }

    public static ComparisonResult compare(EvalRun baseline, EvalRun candidate, Thresholds thresholds) {
        if (baseline == null || candidate == null) {
            return ComparisonResult.invalid("baseline and candidate are required");
        }
        if (baseline.datasetVersion() == null || candidate.datasetVersion() == null
                || !baseline.datasetVersion().equals(candidate.datasetVersion())) {
            return ComparisonResult.invalid(
                    "COMPARISON INVALID: datasetVersion mismatch: "
                            + baseline.datasetVersion() + " vs " + candidate.datasetVersion());
        }
        if (baseline.packDatasetVersion() != null && candidate.packDatasetVersion() != null
                && !baseline.packDatasetVersion().equals(candidate.packDatasetVersion())) {
            return ComparisonResult.invalid(
                    "COMPARISON INVALID: packDatasetVersion mismatch: "
                            + baseline.packDatasetVersion() + " vs " + candidate.packDatasetVersion());
        }
        String protocol = protocolMismatch(baseline, candidate);
        if (protocol != null) {
            return ComparisonResult.invalid(protocol);
        }
        List<String> configDiffs = new ArrayList<>();
        if (baseline.configuration() != null) {
            configDiffs.addAll(baseline.configuration().differences(candidate.configuration()));
        }

        List<MetricDelta> metrics = List.of(
                MetricDelta.of("overallPassRate", baseline.metrics().overallPassRate(), candidate.metrics().overallPassRate(), false),
                MetricDelta.of("contractPassRate", baseline.metrics().contractPassRate(), candidate.metrics().contractPassRate(), false),
                MetricDelta.of("judgeAcceptRate", baseline.metrics().judgeAcceptRate(), candidate.metrics().judgeAcceptRate(), false),
                MetricDelta.of("retrievalPassRate", baseline.metrics().retrievalPassRate(), candidate.metrics().retrievalPassRate(), false),
                MetricDelta.of("negativeCasePassRate", baseline.metrics().negativeCasePassRate(), candidate.metrics().negativeCasePassRate(), false),
                MetricDelta.of("hallucinationRate", baseline.metrics().hallucinationRate(), candidate.metrics().hallucinationRate(), true),
                MetricDelta.of("refusalAccuracy", baseline.metrics().refusalAccuracy(), candidate.metrics().refusalAccuracy(), false),
                MetricDelta.of("layerAccuracy", baseline.metrics().layerAccuracy(), candidate.metrics().layerAccuracy(), false),
                MetricDelta.of("ragAccuracy", baseline.metrics().ragAccuracy(), candidate.metrics().ragAccuracy(), false),
                latencyDelta(baseline, candidate));

        List<CaseComparison> cases = compareCases(baseline, candidate);
        QualityGateResult gate = thresholds == null
                ? null
                : QualityGate.evaluate(candidate, thresholds, baseline);
        return new ComparisonResult(
                true,
                null,
                baseline.runId(),
                candidate.runId(),
                baseline.model(),
                candidate.model(),
                candidate.datasetVersion(),
                configDiffs,
                metrics,
                cases,
                gate);
    }

    /**
     * Live 1-shot (skip red) and nightly (5 reps + red) are different protocols.
     * Null configuration on a legacy run is not treated as a mismatch.
     */
    public static String protocolMismatch(EvalRun baseline, EvalRun candidate) {
        if (candidate == null || candidate.configuration() == null) {
            return null;
        }
        return protocolMismatch(
                baseline,
                candidate.configuration().repetitions(),
                candidate.configuration().includeRed());
    }

    public static String protocolMismatch(EvalRun baseline, int repetitions, boolean includeRed) {
        if (baseline == null || baseline.configuration() == null) {
            return null;
        }
        var c = baseline.configuration();
        if (c.repetitions() != repetitions) {
            return "COMPARISON INVALID: repetitions mismatch: "
                    + c.repetitions() + " vs " + repetitions
                    + " (live 1-shot and nightly 5-rep are different protocols)";
        }
        if (c.includeRed() != includeRed) {
            return "COMPARISON INVALID: includeRed mismatch: "
                    + c.includeRed() + " vs " + includeRed
                    + " (do not compare skip-red live to --red nightly)";
        }
        return null;
    }

    private static MetricDelta latencyDelta(EvalRun baseline, EvalRun candidate) {
        Double bv = baseline.metrics().latency().samples() == 0 ? null : (double) baseline.metrics().latency().avgMs();
        Double cv = candidate.metrics().latency().samples() == 0 ? null : (double) candidate.metrics().latency().avgMs();
        if (bv == null || cv == null) {
            return new MetricDelta("latencyAvgMs", bv, cv, null, eval.domain.DeltaDirection.UNCHANGED, true);
        }
        double d = cv - bv;
        eval.domain.DeltaDirection dir = Math.abs(d) < 1e-9
                ? eval.domain.DeltaDirection.UNCHANGED
                : (d < 0 ? eval.domain.DeltaDirection.IMPROVED : eval.domain.DeltaDirection.REGRESSED);
        return new MetricDelta("latencyAvgMs", bv, cv, d, dir, true);
    }

    static List<CaseComparison> compareCases(EvalRun baseline, EvalRun candidate) {
        Map<String, CaseResult> b = index(baseline);
        Map<String, CaseResult> c = index(candidate);
        List<String> ids = new ArrayList<>();
        for (String id : b.keySet()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        for (String id : c.keySet()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        List<CaseComparison> out = new ArrayList<>();
        for (String id : ids) {
            CaseResult left = b.get(id);
            CaseResult right = c.get(id);
            if (left == null) {
                out.add(new CaseComparison(id, CaseRegression.ADDED, Rate.empty(), success(right)));
                continue;
            }
            if (right == null) {
                out.add(new CaseComparison(id, CaseRegression.REMOVED, success(left), Rate.empty()));
                continue;
            }
            boolean basePass = isPass(left);
            boolean candPass = isPass(right);
            CaseRegression reg;
            if (basePass && candPass) {
                reg = CaseRegression.STILL_PASSING;
            } else if (!basePass && !candPass) {
                reg = CaseRegression.STILL_FAILING;
            } else if (basePass) {
                reg = CaseRegression.NEW_FAILURE;
            } else {
                reg = CaseRegression.RECOVERED;
            }
            out.add(new CaseComparison(id, reg, success(left), success(right)));
        }
        return List.copyOf(out);
    }

    static boolean isPass(CaseResult cse) {
        if (cse.status() == EvalStatus.PASS) {
            return true;
        }
        Rate rate = cse.successRate();
        return rate != null && rate.defined() && rate.hits() == rate.total() && rate.total() > 0;
    }

    static Rate success(CaseResult cse) {
        if (cse.successRate() != null && cse.successRate().defined()) {
            return cse.successRate();
        }
        return Rate.of(cse.passedAttempts(), cse.qualityAttempts());
    }

    private static Map<String, CaseResult> index(EvalRun run) {
        Map<String, CaseResult> map = new LinkedHashMap<>();
        if (run.cases() == null) {
            return map;
        }
        for (CaseResult cse : run.cases()) {
            map.put(cse.caseId(), cse);
        }
        return map;
    }
}
