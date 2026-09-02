package eval.reporting;

import eval.domain.CaseComparison;
import eval.domain.CaseRegression;
import eval.domain.ComparisonResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.GateRuleResult;
import eval.domain.MetricDelta;
import eval.domain.QualityGateResult;
import eval.domain.Rate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConsoleReporter {

    private ConsoleReporter() {
    }

    public static String render(EvalRun run, ComparisonResult comparison) {
        StringBuilder out = new StringBuilder();
        out.append("AI EVAL\n");
        out.append("=======\n");
        out.append("Model:        ").append(run.model()).append('\n');
        out.append("Judge:        ").append(run.judgeModel() == null ? "off" : run.judgeModel()).append('\n');
        out.append("Dataset:      ").append(run.datasetVersion());
        if (run.packDatasetVersion() != null) {
            out.append(" / ").append(run.packDatasetVersion());
        }
        out.append('\n');
        if (run.experimentId() != null) {
            out.append("Experiment:   ").append(run.experimentId()).append('\n');
        }
        out.append("Commit:       ").append(run.gitCommit()).append('\n');
        int reps = run.configuration() == null ? 1 : run.configuration().repetitions();
        out.append("Repetitions:  ").append(reps).append('\n');
        out.append("EXECUTION\n");
        out.append("---------\n");
        out.append("Cases:        ").append(run.casesTotal()).append('\n');
        out.append("Executed:     ").append(run.casesExecuted()).append('\n');
        out.append("Passed:       ").append(run.casesPassed()).append('\n');
        out.append("Failed:       ").append(run.casesFailed()).append('\n');
        out.append("Skipped:      ").append(run.casesSkipped()).append('\n');
        out.append("Errors:       ").append(run.casesError()).append('\n');
        out.append("Attempts:     ").append(run.attemptsPassed()).append(" pass / ")
                .append(run.attemptsFailed()).append(" fail / ")
                .append(run.attemptsError()).append(" error / ")
                .append(run.attemptsSkipped()).append(" skip\n");
        out.append("Pass rate:    ").append(pctOfExecuted(run)).append('\n');
        out.append("Coverage:     ").append(run.coverage().asPercent()).append('\n');
        out.append("METRICS\n");
        out.append("-------\n");
        EvalMetrics m = run.metrics();
        line(out, "Overall", m.overallPassRate().asPercentWithCi());
        line(out, "Contract", m.contractPassRate().asPercent());
        line(out, "Judge", m.judgeAcceptRate().asPercent());
        line(out, "Retrieval", m.retrievalPassRate().asPercent());
        line(out, "Negative", m.negativeCasePassRate().asPercent());
        line(out, "Refusal", m.refusalAccuracy().asPercent());
        line(out, "Hallucination (fail rate)", m.hallucinationRate().asPercent());
        line(out, "Layer", m.layerAccuracy().asPercent());
        line(out, "RAG", m.ragAccuracy().asPercent());
        line(out, "Unstable", m.unstableCaseRate().asPercent());
        if (m.slices() != null) {
            for (Map.Entry<String, Rate> e : m.slices().entrySet()) {
                line(out, MarkdownReporter.sliceLabel(e.getKey()), e.getValue().asPercent());
            }
        }
        if (m.weightedScore() != null) {
            out.append("Weighted:     ")
                    .append(String.format(Locale.ROOT, "%.3f", m.weightedScore()))
                    .append(" (secondary)\n");
        }
        out.append("Latency avg:  ").append(m.latency().avgMs()).append(" ms\n");
        out.append("Latency p95:  ").append(m.latency().p95Ms()).append(" ms\n");
        if (comparison != null) {
            out.append("REGRESSION\n");
            out.append("----------\n");
            if (!comparison.valid()) {
                out.append("COMPARISON INVALID\n");
                out.append(comparison.invalidReason()).append('\n');
            } else {
                out.append("Unchanged pass: ").append(comparison.unchangedPass()).append('\n');
                out.append("Unchanged fail: ").append(comparison.unchangedFail()).append('\n');
                out.append("Regressions:    ").append(comparison.regressions()).append('\n');
                out.append("Improvements:   ").append(comparison.improvements()).append('\n');
                for (MetricDelta d : comparison.metrics()) {
                    if (List.of("overallPassRate", "contractPassRate", "retrievalPassRate", "ragAccuracy", "hallucinationRate", "latencyAvgMs")
                            .contains(d.name())) {
                        out.append(pad(d.name())).append(MarkdownReporter.formatDelta(d))
                                .append("  ").append(d.direction()).append('\n');
                    }
                }
                out.append("New failures:\n");
                printCases(out, comparison, CaseRegression.NEW_FAILURE);
                out.append("Infra errors:\n");
                printCases(out, comparison, CaseRegression.NEW_ERROR);
                out.append("Recovered:\n");
                printCases(out, comparison, CaseRegression.RECOVERED);
            }
        }
        out.append("QUALITY GATE\n");
        out.append("------------\n");
        QualityGateResult gate = comparison != null && comparison.qualityGate() != null
                ? comparison.qualityGate()
                : run.qualityGate();
        if (gate == null) {
            out.append("SKIPPED\n");
        } else {
            out.append(gate.verdict()).append('\n');
            boolean anyFail = false;
            for (GateRuleResult rule : gate.rules()) {
                if (!rule.passed()) {
                    anyFail = true;
                    out.append("- ").append(rule.name()).append(": ").append(rule.detail()).append('\n');
                }
            }
            if (gate.passed()) {
                out.append("Allowed because every recorded rule passed.\n");
            } else if (!anyFail) {
                out.append("- (no rule details)\n");
            }
        }
        return out.toString();
    }

    public static String renderBenchmark(List<EvalRun> runs) {
        StringBuilder out = new StringBuilder();
        out.append("MODEL BENCHMARK\n");
        out.append("===============\n");
        out.append(String.format(Locale.ROOT, "%-24s %10s %10s %10s %14s%n",
                "Model", "Overall", "Contract", "RAG", "Hallucination"));
        out.append("-----------------------------------------------------------------------\n");
        for (EvalRun run : runs) {
            EvalMetrics m = run.metrics();
            out.append(String.format(Locale.ROOT, "%-24s %10s %10s %10s %14s%n",
                    truncate(run.model(), 24),
                    m.overallPassRate().asPercent(),
                    m.contractPassRate().asPercent(),
                    m.ragAccuracy().asPercent(),
                    m.hallucinationRate().asPercent()));
        }
        return out.toString();
    }

    private static String pctOfExecuted(EvalRun run) {
        Rate overall = run.metrics().overallPassRate();
        if (!overall.defined()) {
            return "n/a (no quality attempts)";
        }
        return overall.asPercentWithCi() + " of executed";
    }

    private static void printCases(StringBuilder out, ComparisonResult comparison, CaseRegression want) {
        boolean any = false;
        for (CaseComparison c : comparison.cases()) {
            if (c.regression() == want) {
                any = true;
                out.append("- ").append(c.caseId()).append('\n');
            }
        }
        if (!any) {
            out.append("- (none)\n");
        }
    }

    private static void line(StringBuilder out, String name, String value) {
        out.append(pad(name)).append(value).append('\n');
    }

    private static String pad(String name) {
        return String.format(Locale.ROOT, "%-32s", name + ":");
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
