package eval.execution;

import eval.cli.EvalMain;
import eval.cli.ExitCode;
import eval.domain.CaseResult;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.generation.GoldenCase;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Eval execution")
class EvalExecutorTest {

    @TempDir
    Path tmp;

    @Test
    void defaultLivePinsSeparateGeneratorAndJudge() {
        EvalConfig config = EvalConfig.resolve(new String[]{"--mode=live"});
        assertEquals(EvalConfig.DEFAULT_MODEL, config.model());
        assertEquals(EvalConfig.DEFAULT_JUDGE_MODEL, config.judgeModel());
        assertNotEquals(config.model(), config.judgeModel());
    }

    @Test
    void liveJudgeCallsPinnedJudgeModelNotGenerator() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--model=gen-under-test",
                "--judgeModel=judge-model",
                "--judge=true",
                "--output=" + tmp,
                "--artifacts=never"
        });
        List<String> models = new ArrayList<>();
        ModelRunner runner = (system, user, model) -> {
            models.add(model);
            return new ModelResponse(fixtureFor(user), TokenUsage.of(1, 1), 8);
        };
        new EvalExecutor(config, runner).execute();
        Set<String> unique = new LinkedHashSet<>(models);
        assertTrue(unique.contains("gen-under-test"), models.toString());
        assertTrue(unique.contains("judge-model"), models.toString());
    }

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
        assertEquals("pack-v1", run.packDatasetVersion());
        assertTrue(run.datasetHash() != null && run.datasetHash().length() == 64);
        assertTrue(run.packHash() != null && run.packHash().length() == 64);
        assertTrue(run.configFingerprint() != null && run.configFingerprint().length() == 64);
        assertEquals("DETERMINISTIC", run.configuration().mode());
        assertEquals(eval.domain.RunConfiguration.currentJavaVersion(), run.configuration().javaVersion());
        assertTrue(run.metrics().overallPassRate().value() > 0.99);
        assertTrue(run.metrics().retrievalPassRate().value() > 0.99);
        assertTrue(run.qualityGate() != null && run.qualityGate().passed());
        eval.reporting.ArtifactWriter.write(run, config);
        assertTrue(tmp.resolve(run.runId()).resolve("run.json").toFile().isFile());
        assertTrue(tmp.resolve(run.runId()).resolve("summary.json").toFile().isFile());
        assertTrue(tmp.resolve(run.runId()).resolve("eval-report.md").toFile().isFile());
        assertTrue(java.nio.file.Files.isRegularFile(tmp.resolve("LATEST")));
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
        assertEquals(3, run.casesSkipped());
        assertEquals(1.0, run.metrics().overallPassRate().value(), 1e-12);
        assertEquals(5, run.metrics().overallPassRate().total());
        assertEquals(5, run.coverage().hits());
        assertEquals(8, run.coverage().total());
    }

    @Test
    void liveGateAgainstFixtureBaselineFails() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--judge=false",
                "--gate",
                "--baseline=baselines/generation-v1.json",
                "--output=" + tmp,
                "--artifacts=never"
        });
        ModelRunner runner = (system, user, model) -> new ModelResponse(
                fixtureFor(user), TokenUsage.of(1, 1), 8);
        EvalRun run = new EvalExecutor(config, runner).execute();
        assertEquals("FAIL", run.qualityGate().verdict());
        assertTrue(!run.qualityGate().passed());
        assertTrue(run.qualityGate().rules().stream().anyMatch(r -> "baseline".equals(r.name())));
        assertEquals(ExitCode.QUALITY_GATE_FAILED, EvalMain.exit(run, null));
    }

    @Test
    void liveRegressionAgainstFixtureBaselineIsInvalid() {
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--live",
                "--judge=false",
                "--baseline=baselines/generation-v1.json",
                "--output=" + tmp
        });
        assertEquals(ExitCode.COMPARISON_INVALID, code);
    }

    @Test
    void nightlyProtocolAgainstLiveBaselineIsInvalid() {
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--live",
                "--red",
                "--repetitions=5",
                "--judge=false",
                "--baseline=baselines/live-generation-v1.json",
                "--output=" + tmp
        });
        assertEquals(ExitCode.COMPARISON_INVALID, code);
    }

    @Test
    void liveGateAgainstMismatchedProtocolBaselineFails() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--red",
                "--repetitions=5",
                "--judge=false",
                "--gate",
                "--baseline=baselines/live-generation-v1.json",
                "--output=" + tmp,
                "--artifacts=never"
        });
        ModelRunner runner = (system, user, model) -> new ModelResponse(
                fixtureFor(user), TokenUsage.of(1, 1), 8);
        EvalRun run = new EvalExecutor(config, runner).execute();
        assertEquals("FAIL", run.qualityGate().verdict());
        assertTrue(!run.qualityGate().passed());
        assertTrue(run.qualityGate().rules().stream()
                .anyMatch(r -> r.detail() != null && r.detail().contains("COMPARISON INVALID")));
    }

    @Test
    void mixedPassAndErrorIsCaseErrorNotPass() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--judge=false",
                "--repetitions=2",
                "--red",
                "--output=" + tmp,
                "--artifacts=never"
        });
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ModelRunner runner = (system, user, model) -> {
            if (calls.incrementAndGet() % 2 == 0) {
                throw new EvalInfrastructureException(EvalInfrastructureException.TIMEOUT, "timeout");
            }
            return new ModelResponse(fixtureFor(user), TokenUsage.of(1, 1), 8);
        };
        EvalRun run = new EvalExecutor(config, runner).execute();
        assertEquals(8, run.casesError());
        assertEquals(0, run.casesPassed());
        assertEquals(8, run.attemptsPassed());
        assertEquals(8, run.attemptsError());
        assertEquals(ExitCode.INFRASTRUCTURE_FAILURE, EvalMain.exit(run, null));
    }

    @Test
    void liveFlagWithoutModeSelectsLive() {
        EvalConfig config = EvalConfig.resolve(new String[]{"--live", "--judge=false", "--output=" + tmp});
        assertTrue(config.usesModel());
        assertEquals(eval.domain.EvalMode.LIVE, config.mode());
    }

    @Test
    void candidateRegressionComparesJsonWithoutCallingAModel() {
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--gate",
                "--baseline=baselines/generation-v1.json",
                "--candidate=baselines/generation-v1.json",
                "--output=" + tmp,
                "--artifacts=never"
        });
        assertEquals(ExitCode.SUCCESS, code);
    }

    @Test
    void consecutiveDeterministicRunsKeepKindsAndSlicesOrder() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=deterministic", "--artifacts=never", "--output=" + tmp
        });
        EvalRun a = new EvalExecutor(config).execute();
        EvalRun b = new EvalExecutor(config).execute();
        assertEquals(a.cases().size(), b.cases().size());
        for (int i = 0; i < a.cases().size(); i++) {
            assertEquals(
                    List.copyOf(a.cases().get(i).kinds()),
                    List.copyOf(b.cases().get(i).kinds()),
                    a.cases().get(i).caseId());
        }
        assertEquals(
                List.copyOf(a.metrics().slices().keySet()),
                List.copyOf(b.metrics().slices().keySet()));
    }

    @Test
    void liveRetrieverMissIsErrorAndDoesNotCallModel() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live", "--judge=false", "--output=" + tmp, "--artifacts=never"
        });
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ModelRunner runner = (system, user, model) -> {
            calls.incrementAndGet();
            return new ModelResponse(fixtureFor(user), TokenUsage.of(1, 1), 8);
        };
        GoldenCase row = GoldenReader.require("login-valid-e2e");
        var retrieval = eval.grading.RetrievalGrader.grade(row);
        CaseResult result = new EvalExecutor(config, runner).executeLive(
                row, retrieval, new eval.generation.WorkflowPrompt.Built("sys", List.of()));
        assertEquals(EvalStatus.ERROR, result.status());
        assertEquals(0, calls.get());
        assertEquals(eval.provider.EvalInfrastructureException.RETRIEVER_MISS, result.attempts().getFirst().errorKind());
        assertTrue(result.attempts().getFirst().errorMessage().contains("retriever returned no chunks"));
    }

    @Test
    void unknownSplitIsUsage() {
        int code = EvalMain.run(new String[]{
                "--mode=deterministic",
                "--split=nonsense",
                "--output=" + tmp,
                "--artifacts=never"
        });
        assertEquals(ExitCode.USAGE, code);
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
