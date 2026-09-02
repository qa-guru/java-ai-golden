package eval.metrics;

import eval.cli.EvalMain;
import eval.cli.ExitCode;
import eval.comparison.RunComparator;
import eval.domain.AttemptResult;
import eval.domain.CaseKind;
import eval.domain.CaseResult;
import eval.domain.ContractResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.domain.Rate;
import eval.domain.RunConfiguration;
import eval.domain.TokenUsage;
import eval.generation.GoldenCase;
import eval.generation.GoldenReader;
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
@DisplayName("Eval invariants (anti-cheat)")
class EvalInvariantsTest {

    @Test
    void passFailErrorSkippedPartitionAttempts() {
        eval.execution.EvalConfig config = eval.execution.EvalConfig.resolve(new String[]{
                "--mode=live", "--judge=false", "--output=build/eval-inv", "--artifacts=never"
        });
        eval.provider.ModelRunner runner = (system, user, model) -> new eval.provider.ModelResponse(
                eval.generation.GoldenReader.fixture(idOf(user)), TokenUsage.of(1, 1), 8);
        EvalRun run = new eval.execution.EvalExecutor(config, runner).execute();
        int sum = run.attemptsPassed() + run.attemptsFailed() + run.attemptsError() + run.attemptsSkipped();
        assertEquals(run.attemptsTotal(), sum);
        int caseSum = run.casesPassed() + run.casesFailed() + run.casesError() + run.casesSkipped();
        assertEquals(run.casesTotal(), caseSum);
        assertTrue(run.metrics().overallPassRate().value() >= 0);
        assertTrue(run.metrics().overallPassRate().value() <= 1);
    }

    @Test
    void candidateEqualBaselineHasNoRegression() {
        EvalRun run = fixtureRun();
        var cmp = RunComparator.compare(run, run);
        assertTrue(cmp.valid());
        assertEquals(0, cmp.regressions());
    }

    @Test
    void allSkippedIsNotSuccess() {
        CaseResult skipped = new CaseResult(
                "x",
                EvalStatus.SKIPPED,
                Set.of(CaseKind.GENERATION),
                List.of(AttemptResult.skipped(1, "red")),
                null,
                null,
                eval.domain.RetrievalResult.notApplicable(),
                Rate.empty(),
                List.of(),
                0,
                Map.of());
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(skipped), null);
        EvalRun run = EvalRun.of(
                "r", "t", "m", null, "generation-v1", "pack-v1", "abc",
                new RunConfiguration("LIVE", "m", null, false, 1, false, "NEVER", "build", "ollama"),
                1, 0, 0, 1, 0, 1, 0, 0, 0, metrics, List.of(skipped), 1, null);
        assertEquals(ExitCode.QUALITY_GATE_FAILED, EvalMain.exit(run, null));
    }

    @Test
    void emptyRunIsNotSuccess() {
        EvalMetrics metrics = MetricsAggregator.aggregate(List.of(), null);
        EvalRun run = EvalRun.of(
                "r", "t", "m", null, "generation-v1", "pack-v1", "abc",
                new RunConfiguration("DETERMINISTIC", "m", null, false, 1, false, "NEVER", "build", "ollama"),
                0, 0, 0, 0, 0, 0, 0, 0, 0, metrics, List.of(), 1, null);
        assertEquals(ExitCode.QUALITY_GATE_FAILED, EvalMain.exit(run, null));
    }

    private static EvalRun fixtureRun() {
        eval.execution.EvalConfig config = eval.execution.EvalConfig.resolve(new String[]{
                "--mode=deterministic", "--artifacts=never", "--output=build/eval-inv2"
        });
        return new eval.execution.EvalExecutor(config).execute();
    }

    private static String idOf(String user) {
        for (GoldenCase row : GoldenReader.loadAll()) {
            if (user.contains("golden.id=" + row.id()) || user.equals(row.prompt())) {
                return row.id();
            }
        }
        throw new IllegalStateException(user);
    }
}
