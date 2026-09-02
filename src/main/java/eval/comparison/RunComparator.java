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
import eval.domain.RunConfiguration;
import eval.domain.Thresholds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        if (!java.util.Objects.equals(baseline.datasetHash(), candidate.datasetHash())) {
            return ComparisonResult.invalid(
                    "COMPARISON INVALID: datasetHash mismatch (dataset content changed without a version bump, or a live snapshot is missing datasetHash)");
        }
        if (baseline.packDatasetVersion() != null && candidate.packDatasetVersion() != null
                && !baseline.packDatasetVersion().equals(candidate.packDatasetVersion())) {
            return ComparisonResult.invalid(
                    "COMPARISON INVALID: packDatasetVersion mismatch: "
                            + baseline.packDatasetVersion() + " vs " + candidate.packDatasetVersion());
        }
        String judge = judgeMismatch(baseline, candidate);
        if (judge != null) {
            return ComparisonResult.invalid(judge);
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
        int unchangedPass = 0;
        int unchangedFail = 0;
        int regressions = 0;
        int improvements = 0;
        for (CaseComparison c : cases) {
            switch (c.regression()) {
                case UNCHANGED_PASS, STILL_PASSING -> unchangedPass++;
                case UNCHANGED_FAIL, STILL_FAILING -> unchangedFail++;
                case NEW_FAILURE -> regressions++;
                case RECOVERED -> improvements++;
                default -> {
                }
            }
        }
        McNemar mcnemar = McNemar.of(regressions, improvements);
        QualityGateResult gate = thresholds == null
                ? null
                : QualityGate.evaluate(candidate, thresholds, baseline, cases);
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
                gate,
                unchangedPass,
                unchangedFail,
                regressions,
                improvements,
                mcnemar);
    }

    static String judgeMismatch(EvalRun baseline, EvalRun candidate) {
        RunConfiguration b = baseline.configuration();
        RunConfiguration c = candidate.configuration();
        if (b == null || c == null) {
            return null;
        }
        if (!b.judgeEnabled() && !c.judgeEnabled()) {
            return null;
        }
        if (b.judgeEnabled() != c.judgeEnabled()) {
            return "COMPARISON INVALID: judgeEnabled mismatch: "
                    + b.judgeEnabled() + " vs " + c.judgeEnabled();
        }
        if (!Objects.equals(nz(b.judgeModel()), nz(c.judgeModel()))) {
            return "COMPARISON INVALID: judgeModel mismatch: "
                    + b.judgeModel() + " vs " + c.judgeModel();
        }
        return null;
    }

    /**
     * Live 1-shot (skip red) and nightly (5 reps + red) are different protocols.
     * Null configuration on a legacy run is not treated as a mismatch.
     */
    public static String protocolMismatch(EvalRun baseline, EvalRun candidate) {
        if (candidate == null || candidate.configuration() == null) {
            return null;
        }
        String mode = executionModeMismatch(baseline, candidate);
        if (mode != null) {
            return mode;
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

    static String executionModeMismatch(EvalRun baseline, EvalRun candidate) {
        String left = executionMode(baseline);
        String right = executionMode(candidate);
        if (left == null || right == null) {
            return null;
        }
        if (!left.equals(right)) {
            return "COMPARISON INVALID: execution mode mismatch: " + left + " vs " + right
                    + " (fixture vs live is not a model regression)";
        }
        return null;
    }

    static String executionMode(EvalRun run) {
        if (run == null || run.configuration() == null || run.configuration().mode() == null) {
            return null;
        }
        String mode = run.configuration().mode();
        if ("DETERMINISTIC".equals(mode)) {
            return "DETERMINISTIC";
        }
        if ("LIVE".equals(mode) || "BENCHMARK".equals(mode)) {
            return "LIVE";
        }
        if ("REGRESSION".equals(mode)) {
            return null;
        }
        return mode;
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

    public static List<CaseComparison> compareCases(EvalRun baseline, EvalRun candidate) {
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
            boolean baseQuality = isQuality(left);
            boolean candQuality = isQuality(right);
            CaseRegression reg;
            if (!baseQuality && !candQuality) {
                reg = left.status() == EvalStatus.SKIPPED && right.status() == EvalStatus.SKIPPED
                        ? CaseRegression.UNCHANGED_SKIPPED
                        : CaseRegression.UNCHANGED_FAIL;
            } else if (baseQuality && candQuality) {
                boolean basePass = isPass(left);
                boolean candPass = isPass(right);
                if (basePass && candPass) {
                    reg = CaseRegression.UNCHANGED_PASS;
                } else if (!basePass && !candPass) {
                    reg = CaseRegression.UNCHANGED_FAIL;
                } else if (basePass) {
                    reg = CaseRegression.NEW_FAILURE;
                } else {
                    reg = CaseRegression.RECOVERED;
                }
            } else if (baseQuality && isPass(left) && !candQuality) {
                reg = CaseRegression.NEW_FAILURE;
            } else if (!baseQuality && candQuality && isPass(right)) {
                reg = CaseRegression.RECOVERED;
            } else {
                reg = CaseRegression.UNCHANGED_FAIL;
            }
            out.add(new CaseComparison(id, reg, success(left), success(right)));
        }
        return List.copyOf(out);
    }

    static boolean isQuality(CaseResult cse) {
        return cse.status() == EvalStatus.PASS || cse.status() == EvalStatus.FAIL;
    }

    static boolean isPass(CaseResult cse) {
        return cse.status() == EvalStatus.PASS;
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
