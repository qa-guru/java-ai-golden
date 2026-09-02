package eval.reporting;

import eval.domain.CaseComparison;
import eval.domain.CaseRegression;
import eval.domain.CaseResult;
import eval.domain.ComparisonResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.domain.GateRuleResult;
import eval.domain.MetricDelta;
import eval.domain.QualityGateResult;
import eval.domain.Rate;

import java.util.Locale;

public final class MarkdownReporter {

    private MarkdownReporter() {
    }

    public static String render(EvalRun run, ComparisonResult comparison) {
        StringBuilder md = new StringBuilder();
        md.append("# AI eval report\n\n");
        md.append("- Run: `").append(run.runId()).append("`\n");
        md.append("- Model: `").append(run.model()).append("`\n");
        md.append("- Judge: `").append(run.judgeModel() == null ? "off" : run.judgeModel()).append("`\n");
        md.append("- Dataset: `").append(run.datasetVersion()).append("`\n");
        md.append("- Commit: `").append(run.gitCommit()).append("`\n");
        if (run.configuration() != null) {
            md.append("- Mode: `").append(run.configuration().mode()).append("`\n");
            md.append("- Repetitions: ").append(run.configuration().repetitions()).append('\n');
        }
        md.append("- Duration: ").append(run.durationMs()).append(" ms\n\n");

        md.append("## Cases\n\n");
        md.append("| | count |\n|---|---|\n");
        md.append("| Total | ").append(run.casesTotal()).append(" |\n");
        md.append("| Passed | ").append(run.casesPassed()).append(" |\n");
        md.append("| Failed | ").append(run.casesFailed()).append(" |\n");
        md.append("| Skipped | ").append(run.casesSkipped()).append(" |\n");
        md.append("| Error | ").append(run.casesError()).append(" |\n");
        md.append("| Attempts passed | ").append(run.attemptsPassed()).append(" / ")
                .append(run.attemptsPassed() + run.attemptsFailed()).append(" |\n\n");

        md.append("## Metrics\n\n");
        EvalMetrics m = run.metrics();
        md.append("| metric | value |\n|---|---|\n");
        row(md, "Overall", m.overallPassRate());
        row(md, "Contract", m.contractPassRate());
        row(md, "Judge", m.judgeAcceptRate());
        row(md, "Retrieval", m.retrievalPassRate());
        row(md, "Negative", m.negativeCasePassRate());
        row(md, "Hallucination (fail rate)", m.hallucinationRate());
        row(md, "Refusal", m.refusalAccuracy());
        row(md, "Layer", m.layerAccuracy());
        row(md, "RAG", m.ragAccuracy());
        if (m.weightedScore() != null) {
            md.append("| Weighted (secondary) | ")
                    .append(String.format(Locale.ROOT, "%.3f", m.weightedScore()))
                    .append(" |\n");
            md.append("| Weighted formula | `").append(m.weightedScoreFormula()).append("` |\n");
        }
        md.append("| Latency avg | ").append(m.latency().avgMs()).append(" ms |\n");
        md.append("| Latency p95 | ").append(m.latency().p95Ms()).append(" ms |\n\n");

        md.append("## Failures\n\n");
        boolean anyFail = false;
        for (CaseResult cse : run.cases()) {
            if (cse.status() == EvalStatus.FAIL || cse.status() == EvalStatus.ERROR) {
                anyFail = true;
                md.append("- `").append(cse.caseId()).append("` ").append(cse.status());
                md.append(" ").append(cse.successRate().asFraction());
                if (!cse.errors().isEmpty()) {
                    md.append(" — ").append(String.join("; ", cse.errors()));
                } else if (cse.contract() != null && !cse.contract().passed()) {
                    md.append(" — ").append(String.join("; ", cse.contract().violations()));
                }
                md.append('\n');
            }
        }
        if (!anyFail) {
            md.append("_None._\n");
        }
        md.append('\n');

        if (comparison != null) {
            md.append(renderComparison(comparison));
        }

        md.append("## Quality gate\n\n");
        QualityGateResult gate = comparison != null && comparison.qualityGate() != null
                ? comparison.qualityGate()
                : run.qualityGate();
        if (gate == null) {
            md.append("_Not applied._\n");
        } else {
            md.append("**").append(gate.verdict()).append("**\n\n");
            for (GateRuleResult rule : gate.rules()) {
                md.append("- `").append(rule.name()).append("` ")
                        .append(rule.passed() ? "PASS" : "FAIL")
                        .append(" — ").append(rule.detail()).append('\n');
            }
        }
        md.append('\n');
        return md.toString();
    }

    public static String renderComparison(ComparisonResult comparison) {
        StringBuilder md = new StringBuilder();
        md.append("## Regression\n\n");
        if (!comparison.valid()) {
            md.append("**COMPARISON INVALID** — ").append(comparison.invalidReason()).append("\n\n");
            return md.toString();
        }
        md.append("- Baseline: `").append(comparison.baselineModel()).append("` run `")
                .append(comparison.baselineRunId()).append("`\n");
        md.append("- Candidate: `").append(comparison.candidateModel()).append("` run `")
                .append(comparison.candidateRunId()).append("`\n");
        md.append("- Dataset: `").append(comparison.datasetVersion()).append("`\n");
        if (!comparison.configurationDifferences().isEmpty()) {
            md.append("- Config differences:\n");
            for (String d : comparison.configurationDifferences()) {
                md.append("  - ").append(d).append('\n');
            }
        }
        md.append('\n');
        md.append("| metric | delta | direction |\n|---|---|---|\n");
        for (MetricDelta d : comparison.metrics()) {
            md.append("| ").append(d.name()).append(" | ")
                    .append(formatDelta(d)).append(" | ")
                    .append(d.direction()).append(" |\n");
        }
        md.append('\n');
        md.append("### New failures\n\n");
        appendCaseList(md, comparison, CaseRegression.NEW_FAILURE);
        md.append("### Recovered\n\n");
        appendCaseList(md, comparison, CaseRegression.RECOVERED);
        md.append('\n');
        return md.toString();
    }

    private static void appendCaseList(StringBuilder md, ComparisonResult comparison, CaseRegression want) {
        boolean any = false;
        for (CaseComparison c : comparison.cases()) {
            if (c.regression() == want) {
                any = true;
                md.append("- `").append(c.caseId()).append("` ")
                        .append(c.baselineSuccess().asPercent())
                        .append(" → ")
                        .append(c.candidateSuccess().asPercent())
                        .append('\n');
            }
        }
        if (!any) {
            md.append("_None._\n");
        }
        md.append('\n');
    }

    private static void row(StringBuilder md, String name, Rate rate) {
        md.append("| ").append(name).append(" | ").append(SummaryView.formatRate(rate)).append(" |\n");
    }

    static String formatDelta(MetricDelta d) {
        if (d.delta() == null) {
            return "n/a";
        }
        if (d.name().equals("latencyAvgMs")) {
            return String.format(Locale.ROOT, "%+.0f ms", d.delta());
        }
        return String.format(Locale.ROOT, "%+.1f%%", d.delta() * 100.0);
    }
}
