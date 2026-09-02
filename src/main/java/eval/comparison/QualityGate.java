package eval.comparison;

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
        if (run == null || run.metrics() == null) {
            return QualityGateResult.fail(List.of(
                    new GateRuleResult("run", "missing eval run", false, null, null, "absolute")));
        }
        if (absolute == null) {
            absolute = Thresholds.none();
        }
        List<GateRuleResult> rules = new ArrayList<>();
        EvalMetrics m = run.metrics();
        addMin(rules, "overallPassRate", m.overallPassRate(), absolute.overallPassRate());
        addMin(rules, "contractPassRate", m.contractPassRate(), absolute.contractPassRate());
        addMin(rules, "judgeAcceptRate", m.judgeAcceptRate(), absolute.judgeAcceptRate());
        addMin(rules, "retrievalPassRate", m.retrievalPassRate(), absolute.retrievalPassRate());
        addMin(rules, "negativeCasePassRate", m.negativeCasePassRate(), absolute.negativeCasePassRate());
        addMin(rules, "refusalAccuracy", m.refusalAccuracy(), absolute.refusalAccuracy());
        addMin(rules, "layerAccuracy", m.layerAccuracy(), absolute.layerAccuracy());
        addMin(rules, "ragAccuracy", m.ragAccuracy(), absolute.ragAccuracy());
        addMax(rules, "hallucinationRate", m.hallucinationRate(), absolute.hallucinationRate());

        if (baseline != null && absolute.allowedRegression() != null && baseline.metrics() != null) {
            double allowed = absolute.allowedRegression();
            EvalMetrics b = baseline.metrics();
            addDeltaMin(rules, "overallPassRate", b.overallPassRate(), m.overallPassRate(), allowed);
            addDeltaMin(rules, "contractPassRate", b.contractPassRate(), m.contractPassRate(), allowed);
            addDeltaMin(rules, "judgeAcceptRate", b.judgeAcceptRate(), m.judgeAcceptRate(), allowed);
            addDeltaMin(rules, "retrievalPassRate", b.retrievalPassRate(), m.retrievalPassRate(), allowed);
            addDeltaMin(rules, "negativeCasePassRate", b.negativeCasePassRate(), m.negativeCasePassRate(), allowed);
            addDeltaMin(rules, "refusalAccuracy", b.refusalAccuracy(), m.refusalAccuracy(), allowed);
            addDeltaMin(rules, "layerAccuracy", b.layerAccuracy(), m.layerAccuracy(), allowed);
            addDeltaMin(rules, "ragAccuracy", b.ragAccuracy(), m.ragAccuracy(), allowed);
            addDeltaMax(rules, "hallucinationRate", b.hallucinationRate(), m.hallucinationRate(), allowed);
        }

        boolean passed = rules.stream().allMatch(GateRuleResult::passed);
        return passed ? QualityGateResult.pass(rules) : QualityGateResult.fail(rules);
    }

    private static void addMin(List<GateRuleResult> rules, String name, Rate actual, Double min) {
        if (min == null) {
            return;
        }
        if (!actual.defined()) {
            rules.add(new GateRuleResult(name, "undefined (no attempts)", true, null, min, "absolute"));
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

    private static void addMax(List<GateRuleResult> rules, String name, Rate actual, Double max) {
        if (max == null) {
            return;
        }
        if (!actual.defined()) {
            rules.add(new GateRuleResult(name, "undefined (no attempts)", true, null, max, "absolute"));
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
                ok ? "PASS" : "regressed more than allowed",
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
