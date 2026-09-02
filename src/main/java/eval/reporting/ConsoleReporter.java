package eval.reporting;

import eval.domain.CaseComparison;
import eval.domain.CaseRegression;
import eval.domain.ComparisonResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.MetricDelta;
import eval.domain.QualityGateResult;

import java.util.List;
import java.util.Locale;

public final class ConsoleReporter {

    private ConsoleReporter() {
    }

    public static String render(EvalRun run, ComparisonResult comparison) {
        StringBuilder out = new StringBuilder();
        out.append("AI EVAL\n");
        out.append("=======\n");
        out.append("Model:        ").append(run.model()).append('\n');
        out.append("Judge:        ").append(run.judgeModel() == null ? "off" : run.judgeModel()).append('\n');
        out.append("Dataset:      ").append(run.datasetVersion()).append('\n');
        out.append("Commit:       ").append(run.gitCommit()).append('\n');
        int reps = run.configuration() == null ? 1 : run.configuration().repetitions();
        out.append("Repetitions:  ").append(reps).append('\n');
        out.append("CASES\n");
        out.append("-----\n");
        out.append("Total:        ").append(run.casesTotal()).append('\n');
        out.append("Attempts:     ").append(run.attemptsTotal()).append('\n');
        out.append("Passed:       ").append(run.attemptsPassed()).append('\n');
        out.append("Failed:       ").append(run.attemptsFailed()).append('\n');
        out.append("Errors:       ").append(run.attemptsError()).append('\n');
        out.append("Skipped:      ").append(run.casesSkipped()).append('\n');
        out.append("METRICS\n");
        out.append("-------\n");
        EvalMetrics m = run.metrics();
        line(out, "Overall", m.overallPassRate().asPercent());
        line(out, "Contract", m.contractPassRate().asPercent());
        line(out, "Judge", m.judgeAcceptRate().asPercent());
        line(out, "Retrieval", m.retrievalPassRate().asPercent());
        line(out, "Negative", m.negativeCasePassRate().asPercent());
        line(out, "Refusal", m.refusalAccuracy().asPercent());
        line(out, "Hallucination", m.hallucinationRate().asPercent());
        line(out, "Layer", m.layerAccuracy().asPercent());
        line(out, "RAG", m.ragAccuracy().asPercent());
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
                for (MetricDelta d : comparison.metrics()) {
                    if (List.of("overallPassRate", "contractPassRate", "retrievalPassRate", "ragAccuracy", "hallucinationRate", "latencyAvgMs")
                            .contains(d.name())) {
                        out.append(pad(d.name())).append(MarkdownReporter.formatDelta(d))
                                .append("  ").append(d.direction()).append('\n');
                    }
                }
                out.append("New failures:\n");
                printCases(out, comparison, CaseRegression.NEW_FAILURE);
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
        return String.format(Locale.ROOT, "%-16s", name + ":");
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
