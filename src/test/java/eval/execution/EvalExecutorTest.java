package eval.execution;

import eval.cli.EvalMain;
import eval.cli.ExitCode;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.generation.GoldenReader;
import eval.provider.EvalInfrastructureException;
import eval.provider.ModelResponse;
import eval.provider.ModelRunner;
import eval.domain.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Eval execution")
class EvalExecutorTest {

    @TempDir
    Path tmp;

    @Test
    void deterministicRunPassesAllFixturesAndRetrieval() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=deterministic",
                "--gate",
                "--output=" + tmp,
                "--artifacts=always"
        });
        EvalRun run = new EvalExecutor(config).execute();
        assertEquals(8, run.casesTotal());
        assertEquals(8, run.casesPassed());
        assertEquals(0, run.casesFailed());
        assertEquals(0, run.casesError());
        assertEquals("generation-v1", run.datasetVersion());
        assertTrue(run.metrics().overallPassRate().value() > 0.99);
        assertTrue(run.metrics().retrievalPassRate().value() > 0.99);
        assertTrue(run.qualityGate() != null && run.qualityGate().passed());
        eval.reporting.ArtifactWriter.write(run, config);
        assertTrue(tmp.resolve(run.runId()).resolve("run.json").toFile().isFile());
        assertTrue(tmp.resolve(run.runId()).resolve("summary.json").toFile().isFile());
        assertTrue(tmp.resolve(run.runId()).resolve("eval-report.md").toFile().isFile());
    }

    @Test
    void liveRepetitionsAggregatePerCase() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--judge=false",
                "--repetitions=5",
                "--red",
                "--output=" + tmp,
                "--artifacts=never"
        });
        ModelRunner runner = (system, user, model) -> new ModelResponse(
                fixtureFor(user), TokenUsage.of(10, 20), 12);
        EvalRun run = new EvalExecutor(config, runner).execute();
        assertEquals(0, run.casesError());
        assertEquals(8, run.casesPassed());
        assertEquals(40, run.attemptsPassed());
        run.cases().forEach(cse -> assertEquals(5, cse.successRate().hits()));
    }

    @Test
    void modelUnavailableIsErrorNotQualityZero() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--judge=false",
                "--output=" + tmp,
                "--red"
        });
        ModelRunner runner = (system, user, model) -> {
            throw new EvalInfrastructureException(EvalInfrastructureException.MODEL_UNAVAILABLE, "ollama down");
        };
        EvalRun run = new EvalExecutor(config, runner).execute();
        assertEquals(8, run.casesError());
        assertEquals(0, run.casesFailed());
        assertTrue(!run.metrics().overallPassRate().defined());
        assertEquals(ExitCode.INFRASTRUCTURE_FAILURE, EvalMain.exit(run, null));
    }

    @Test
    void redRowsSkipWithoutFlag() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--judge=false",
                "--output=" + tmp
        });
        ModelRunner runner = (system, user, model) -> new ModelResponse(
                fixtureFor(user), TokenUsage.of(1, 1), 8);
        EvalRun run = new EvalExecutor(config, runner).execute();
        long skipped = run.cases().stream().filter(c -> c.status() == EvalStatus.SKIPPED).count();
        assertEquals(3, skipped);
        assertEquals(5, run.casesPassed());
    }

    private static String fixtureFor(String user) {
        for (var row : GoldenReader.loadAll()) {
            if (user.contains("golden.id=" + row.id()) || user.equals(row.prompt())) {
                return GoldenReader.fixture(row.id());
            }
        }
        throw new IllegalStateException("no fixture for user: " + user);
    }
}
