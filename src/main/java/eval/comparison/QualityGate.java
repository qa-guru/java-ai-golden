package eval.comparison;

import eval.domain.CaseComparison;
import eval.domain.CaseRegression;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.GateRuleResult;
import eval.domain.QualityGateResult;
import eval.domain.Rate;
import eval.domain.Thresholds;

import java.util.ArrayList;
import java.util.List;

public final class QualityGate {

    private QualityGate() {
    }

    public static QualityGateResult evaluate(EvalRun run, Thresholds absolute, EvalRun baseline) {
        List<CaseComparison> pairs = baseline == null ? List.of() : RunComparator.compareCases(baseline, run);
        return evaluate(run, absolute, baseline, pairs);
    }

    public static QualityGateResult evaluate(
            EvalRun run, Thresholds absolute, EvalRun baseline, List<CaseComparison> pairs) {
        if (run == null || run.metrics() == null) {
            return QualityGateResult.fail(List.of(
                    new GateRuleResult("run", "missing eval run", false, null, null, "absolute")));
        }
        if (absolute == null) {
            absolute = Thresholds.none();
        }
        List<GateRuleResult> rules = new ArrayList<>();
        EvalMetrics m = run.metrics();

        if (run.hasNoQualityAttempts()) {
            String why = run.attemptsError() > 0
                    ? "no quality attempts (all infrastructure errors)"
                    : "no quality attempts (empty dataset or all skipped)";
            rules.add(new GateRuleResult("qualityAttempts", why, false, 0.0, 1.0, "execution"));
        }

        addMin(rules, "overallPassRate", m.overallPassRate(), absolute.overallPassRate(), true);
        addMin(rules, "contractPassRate", m.contractPassRate(), absolute.contractPassRate(), true);
        addMin(rules, "judgeAcceptRate", m.judgeAcceptRate(), absolute.judgeAcceptRate(), false);
        addMin(rules, "retrievalPassRate", m.retrievalPassRate(), absolute.retrievalPassRate(), false);
        addMin(rules, "negativeCasePassRate", m.negativeCasePassRate(), absolute.negativeCasePassRate(), false);
        addMin(rules, "refusalAccuracy", m.refusalAccuracy(), absolute.refusalAccuracy(), false);
        addMin(rules, "layerAccuracy", m.layerAccuracy(), absolute.layerAccuracy(), false);
        addMin(rules, "ragAccuracy", m.ragAccuracy(), absolute.ragAccuracy(), false);
        addMax(rules, "hallucinationRate", m.hallucinationRate(), absolute.hallucinationRate(), false);

        if (baseline != null && absolute.allowedRegression() != null && baseline.metrics() != null) {
            double allowed = absolute.allowedRegression();
            EvalMetrics b = baseline.metrics();
            addDeltaMin(rules, "overallPassRate", b.overallPassRate(), m.overallPassRate(), allowed);
            addDeltaMin(rules, "contractPassRate", b.contractPassRate(), m.contractPassRate(), allowed);
            addDeltaMin(rules, "retrievalPassRate", b.retrievalPassRate(), m.retrievalPassRate(), allowed);
            addDeltaMin(rules, "negativeCasePassRate", b.negativeCasePassRate(), m.negativeCasePassRate(), allowed);
            addDeltaMin(rules, "refusalAccuracy", b.refusalAccuracy(), m.refusalAccuracy(), allowed);
            addDeltaMin(rules, "layerAccuracy", b.layerAccuracy(), m.layerAccuracy(), allowed);
            addDeltaMin(rules, "ragAccuracy", b.ragAccuracy(), m.ragAccuracy(), allowed);
            addDeltaMax(rules, "hallucinationRate", b.hallucinationRate(), m.hallucinationRate(), allowed);
        }

        if (pairs != null) {
            for (CaseComparison pair : pairs) {
                if (pair.regression() == CaseRegression.ADDED) {
                    rules.add(new GateRuleResult(
                            "added." + pair.caseId(),
                            "candidate has a case that is not in the baseline — not a fair comparison",
                            false,
                            null,
                            null,
                            "identity"));
                    continue;
                }
                if (pair.regression() == CaseRegression.REMOVED) {
                    rules.add(new GateRuleResult(
                            "removed." + pair.caseId(),
                            "candidate lost a case that was in the baseline — not a fair comparison",
                            false,
                            null,
                            null,
                            "identity"));
                    continue;
                }
                if (pair.regression() != CaseRegression.NEW_FAILURE) {
                    continue;
                }
                boolean critical = run.cases().stream()
                        .filter(c -> pair.caseId().equals(c.caseId()))
                        .anyMatch(c -> c.hasCriticalViolation());
                if (critical) {
                    rules.add(new GateRuleResult(
                            "criticalNewFailure." + pair.caseId(),
                            "CRITICAL new failure cannot be offset by overall improvement",
                            false,
                            null,
                            null,
                            "severity"));
                }
            }
        }

        boolean passed = rules.stream().allMatch(GateRuleResult::passed);
        return passed ? QualityGateResult.pass(rules) : QualityGateResult.fail(rules);
    }

    public static QualityGateResult unusableBaseline(String why) {
        return QualityGateResult.fail(List.of(
                new GateRuleResult("baseline", why, false, null, null, "execution")));
    }

    private static void addMin(
            List<GateRuleResult> rules, String name, Rate actual, Double min, boolean requiredWhenSet) {
        if (min == null) {
            return;
        }
        if (!actual.defined()) {
            if (requiredWhenSet) {
                rules.add(new GateRuleResult(
                        name, "undefined (no quality attempts) — not 100%", false, null, min, "absolute"));
            }
            return;
        }
        boolean ok = actual.value() + 1e-12 >= min;
        rules.add(new GateRuleResult(
                name,
                ok
                        ? String.format(java.util.Locale.ROOT, "%.3f >= %.3f", actual.value(), min)
                        : String.format(java.util.Locale.ROOT, "%.3f < %.3f", actual.value(), min),
                ok,
                actual.value(),
                min,
                "absolute"));
    }

    private static void addMax(
            List<GateRuleResult> rules, String name, Rate actual, Double max, boolean requiredWhenSet) {
        if (max == null) {
            return;
        }
        if (!actual.defined()) {
            if (requiredWhenSet) {
                rules.add(new GateRuleResult(
                        name, "undefined (no quality attempts)", false, null, max, "absolute"));
            }
            return;
        }
        boolean ok = actual.value() - 1e-12 <= max;
        rules.add(new GateRuleResult(
                name,
                ok
                        ? String.format(java.util.Locale.ROOT, "%.3f <= %.3f", actual.value(), max)
                        : String.format(java.util.Locale.ROOT, "%.3f > %.3f", actual.value(), max),
                ok,
                actual.value(),
                max,
                "absolute"));
    }

    private static void addDeltaMin(List<GateRuleResult> rules, String name, Rate baseline, Rate candidate, double allowed) {
        if (!baseline.defined() || !candidate.defined()) {
            return;
        }
        double floor = baseline.value() - allowed;
        boolean ok = candidate.value() + 1e-12 >= floor;
        rules.add(new GateRuleResult(
                name + ".delta",
                ok
                        ? String.format(
                                java.util.Locale.ROOT,
                                "candidate %.3f >= baseline %.3f - %.3f",
                                candidate.value(),
                                baseline.value(),
                                allowed)
                        : String.format(
                                java.util.Locale.ROOT,
                                "regressed more than allowed: %.3f < %.3f - %.3f",
                                candidate.value(),
                                baseline.value(),
                                allowed),
                ok,
                candidate.value() - baseline.value(),
                -allowed,
                "delta"));
    }

    private static void addDeltaMax(List<GateRuleResult> rules, String name, Rate baseline, Rate candidate, double allowed) {
        if (!baseline.defined() || !candidate.defined()) {
            return;
        }
        double ceil = baseline.value() + allowed;
        boolean ok = candidate.value() - 1e-12 <= ceil;
        rules.add(new GateRuleResult(
                name + ".delta",
                ok ? "PASS" : "increased more than allowed",
                ok,
                candidate.value() - baseline.value(),
                allowed,
                "delta"));
    }
}
