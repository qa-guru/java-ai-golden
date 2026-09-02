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
import eval.domain.Violation;

import java.util.Locale;
import java.util.Map;

public final class MarkdownReporter {

    private MarkdownReporter() {
    }

    public static String render(EvalRun run, ComparisonResult comparison) {
        StringBuilder md = new StringBuilder();
        md.append("# AI eval report\n\n");
        md.append("- Run: `").append(run.runId()).append("`\n");
        md.append("- Model: `").append(run.model()).append("`\n");
        md.append("- Judge: `").append(run.judgeModel() == null ? "off" : run.judgeModel()).append("`\n");
        md.append("- Dataset: `").append(run.datasetVersion()).append("`");
        if (run.packDatasetVersion() != null) {
            md.append(" / `").append(run.packDatasetVersion()).append("`");
        }
        md.append('\n');
        if (run.datasetHash() != null) {
            md.append("- Dataset hash: `").append(run.datasetHash()).append("`\n");
        }
        if (run.packHash() != null) {
            md.append("- Pack hash: `").append(run.packHash()).append("`\n");
        }
        if (run.experimentId() != null) {
            md.append("- Experiment: `").append(run.experimentId()).append("`\n");
        }
        if (run.configFingerprint() != null) {
            md.append("- Config fingerprint: `").append(run.configFingerprint()).append("`\n");
        }
        md.append("- Commit: `").append(run.gitCommit()).append("`\n");
        if (run.configuration() != null) {
            md.append("- Mode: `").append(run.configuration().mode()).append("`\n");
            md.append("- Provider: `").append(run.configuration().provider()).append("`\n");
            md.append("- Repetitions: ").append(run.configuration().repetitions()).append('\n');
            if (run.configuration().javaVersion() != null) {
                md.append("- Java: `").append(run.configuration().javaVersion()).append("`\n");
            }
            if (run.configuration().datasetSplit() != null) {
                md.append("- Split: `").append(run.configuration().datasetSplit()).append("`\n");
            }
        }
        md.append("- Duration: ").append(run.durationMs()).append(" ms\n\n");

        md.append("## Execution\n\n");
        md.append("| | count |\n|---|---|\n");
        md.append("| Cases | ").append(run.casesTotal()).append(" |\n");
        md.append("| Executed | ").append(run.casesExecuted()).append(" |\n");
        md.append("| Passed | ").append(run.casesPassed()).append(" |\n");
        md.append("| Failed | ").append(run.casesFailed()).append(" |\n");
        md.append("| Skipped | ").append(run.casesSkipped()).append(" |\n");
        md.append("| Error | ").append(run.casesError()).append(" |\n");
        md.append("| Attempts | ").append(run.attemptsTotal()).append(" |\n");
        md.append("| Attempts passed | ").append(run.attemptsPassed()).append(" |\n");
        md.append("| Attempts failed | ").append(run.attemptsFailed()).append(" |\n");
        md.append("| Attempts skipped | ").append(run.attemptsSkipped()).append(" |\n");
        md.append("| Attempts error | ").append(run.attemptsError()).append(" |\n");
        md.append("| Pass rate (of executed quality attempts) | ")
                .append(SummaryView.formatRate(run.metrics().overallPassRate())).append(" |\n");
        md.append("| Coverage (executed / cases) | ")
                .append(SummaryView.formatRate(run.coverage())).append(" |\n\n");

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
        row(md, "Unstable cases", m.unstableCaseRate());
        if (m.slices() != null && !m.slices().isEmpty()) {
            for (Map.Entry<String, Rate> e : m.slices().entrySet()) {
                row(md, sliceLabel(e.getKey()), e.getValue());
            }
        }
        if (m.weightedScore() != null) {
            md.append("| Weighted (secondary) | ")
                    .append(String.format(Locale.ROOT, "%.3f", m.weightedScore()))
                    .append(" |\n");
            md.append("| Weighted formula | `").append(m.weightedScoreFormula()).append("` |\n");
        }
        md.append("| Latency min/avg/median/p95/max | ")
                .append(m.latency().minMs()).append(" / ")
                .append(m.latency().avgMs()).append(" / ")
                .append(m.latency().medianMs()).append(" / ")
                .append(m.latency().p95Ms()).append(" / ")
                .append(m.latency().maxMs()).append(" ms |\n");
        if (m.tokens() != null && (m.tokens().totalTokens() != null)) {
            md.append("| Tokens in/out/total | ")
                    .append(m.tokens().inputTokens()).append(" / ")
                    .append(m.tokens().outputTokens()).append(" / ")
                    .append(m.tokens().totalTokens()).append(" |\n");
            md.append("| Estimated cost | ")
                    .append(m.tokens().estimatedCost() == null ? "null" : m.tokens().estimatedCost())
                    .append(" |\n");
        }
        md.append('\n');

        md.append("## Failures / taxonomy\n\n");
        boolean anyFail = false;
        for (CaseResult cse : run.cases()) {
            if (cse.status() == EvalStatus.FAIL || cse.status() == EvalStatus.ERROR) {
                anyFail = true;
                md.append("- `").append(cse.caseId()).append("` ").append(cse.status());
                md.append(" ").append(cse.successRate().asFraction());
                if (!cse.taxonomy().isEmpty()) {
                    Violation v = cse.taxonomy().getFirst();
                    md.append(" category: ").append(v.category());
                    md.append(" severity: ").append(v.severity());
                    md.append(" grader: ").append(v.grader());
                    md.append(" reason: ").append(v.reason());
                } else if (!cse.errors().isEmpty()) {
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

        md.append("## Stability\n\n");
        md.append("Quality is pass rate. Stability is whether repetitions agree.\n\n");
        md.append("| case | k/N | unstable |\n|---|---|---|\n");
        boolean anyStab = false;
        for (CaseResult cse : run.cases()) {
            if (cse.successRate() != null && cse.successRate().total() >= 2) {
                anyStab = true;
                md.append("| `").append(cse.caseId()).append("` | ")
                        .append(cse.successRate().asFraction()).append(" | ")
                        .append(cse.unstable() ? "yes" : "no").append(" |\n");
            }
        }
        if (!anyStab) {
            md.append("| _(single attempt)_ | | |\n");
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
        md.append("- Decision: **").append(comparison.decision()).append("**\n");
        md.append("- Baseline: `").append(comparison.baselineModel()).append("` run `")
                .append(comparison.baselineRunId()).append("`\n");
        md.append("- Candidate: `").append(comparison.candidateModel()).append("` run `")
                .append(comparison.candidateRunId()).append("`\n");
        md.append("- Dataset: `").append(comparison.datasetVersion()).append("`\n");
        md.append("- Unchanged pass: ").append(comparison.unchangedPass()).append('\n');
        md.append("- Unchanged fail: ").append(comparison.unchangedFail()).append('\n');
        md.append("- Regressions: ").append(comparison.regressions()).append('\n');
        md.append("- Improvements: ").append(comparison.improvements()).append('\n');
        if (comparison.mcnemar() != null) {
            md.append("- McNemar n01/n10: ")
                    .append(comparison.mcnemar().n01()).append("/")
                    .append(comparison.mcnemar().n10());
            if (comparison.mcnemar().twoSidedPValue() != null) {
                md.append(" p≈").append(String.format(Locale.ROOT, "%.3f", comparison.mcnemar().twoSidedPValue()));
            }
            md.append(" _(informational, not a gate)_\n");
        }
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
        md.append("### Infrastructure errors (not quality regressions)\n\n");
        appendCaseList(md, comparison, CaseRegression.NEW_ERROR);
        md.append("### Recovered\n\n");
        appendCaseList(md, comparison, CaseRegression.RECOVERED);
        md.append("### Unchanged pass\n\n");
        appendCaseList(md, comparison, CaseRegression.UNCHANGED_PASS);
        md.append("### Unchanged fail\n\n");
        appendCaseList(md, comparison, CaseRegression.UNCHANGED_FAIL);
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

    static String sliceLabel(String key) {
        if ("hallucination".equals(key)) {
            return "Slice hallucination (pass rate)";
        }
        return "Slice " + key;
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
