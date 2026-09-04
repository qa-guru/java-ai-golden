package eval.reporting;

import eval.domain.EvalRun;
import eval.execution.EvalConfig;
import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Baseline JSON")
class BaselineIoTest {

    @Test
    void committedHoldoutBaselineIsReadable() {
        EvalRun run = ReportIo.readRun(Path.of("baselines/holdout-v1.json"));
        assertEquals("holdout-v1", run.datasetVersion());
        assertEquals("pack-v1", run.packDatasetVersion());
        assertEquals(8, run.casesTotal());
        assertEquals(8, run.casesPassed());
        assertEquals("DETERMINISTIC", run.configuration().mode());
        assertEquals("holdout", run.configuration().datasetSplit());
        assertEquals(1.0, run.metrics().overallPassRate().value(), 1e-12);
        assertTrue(run.datasetHash() != null && !run.datasetHash().isBlank());
        assertEquals(64, run.packHash().length());
        assertEquals(eval.pack.PackFiles.contentHash(), run.packHash());
    }

    @Test
    void committedDeterministicBaselineIsReadable() {
        EvalRun run = ReportIo.readRun(Path.of("baselines/generation-v1.json"));
        assertEquals("generation-v1", run.datasetVersion());
        assertEquals("pack-v1", run.packDatasetVersion());
        assertEquals(8, run.casesTotal());
        assertEquals(8, run.casesPassed());
        assertTrue(run.metrics().overallPassRate().defined());
        assertEquals(1.0, run.metrics().overallPassRate().value(), 1e-12);
        assertEquals("ollama", run.configuration().provider());
        assertEquals(64, run.packHash().length());
        assertEquals(eval.pack.PackFiles.contentHash(), run.packHash());
    }

    @Test
    void committedLiveBaselineIsReadable() {
        EvalRun run = ReportIo.readRun(Path.of("baselines/live-generation-v1.json"));
        assertEquals("generation-v1", run.datasetVersion());
        assertEquals("pack-v1", run.packDatasetVersion());
        assertEquals(8, run.casesTotal());
        assertEquals(5, run.casesPassed());
        assertEquals(3, run.casesSkipped());
        assertEquals(0, run.casesError());
        assertTrue(run.metrics().overallPassRate().defined());
        assertEquals(1.0, run.metrics().overallPassRate().value(), 1e-12);
        assertEquals("LIVE", run.configuration().mode());
        assertEquals(EvalConfig.DEFAULT_MODEL, run.model());
        assertEquals(EvalConfig.DEFAULT_JUDGE_MODEL, run.judgeModel());
        assertEquals(1, run.configuration().repetitions());
        assertEquals(false, run.configuration().includeRed());
        assertEquals("ollama", run.configuration().provider());
        assertEquals("development", run.configuration().datasetSplit());
        assertTrue(run.datasetHash() != null && run.datasetHash().length() == 64);
        assertEquals(GoldenReader.datasetHash(), run.datasetHash());
        assertEquals(64, run.packHash().length());
        assertEquals(eval.pack.PackFiles.contentHash(), run.packHash());
    }

    @Test
    void committedNightlyBaselineIsReadable() {
        EvalRun run = ReportIo.readRun(Path.of("baselines/nightly-generation-v1.json"));
        assertEquals("generation-v1", run.datasetVersion());
        assertEquals("pack-v1", run.packDatasetVersion());
        assertEquals(8, run.casesTotal());
        assertEquals(5, run.casesPassed());
        assertEquals(3, run.casesFailed());
        assertEquals(0, run.casesSkipped());
        assertEquals(0, run.casesError());
        assertEquals(40, run.attemptsTotal());
        assertEquals(25, run.attemptsPassed());
        assertEquals(15, run.attemptsFailed());
        assertEquals(0.625, run.metrics().overallPassRate().value(), 1e-12);
        assertEquals(10, run.metrics().hallucinationRate().hits());
        assertEquals(10, run.metrics().hallucinationRate().total());
        assertEquals("LIVE", run.configuration().mode());
        assertEquals(EvalConfig.DEFAULT_MODEL, run.model());
        assertEquals(EvalConfig.DEFAULT_JUDGE_MODEL, run.judgeModel());
        assertEquals(5, run.configuration().repetitions());
        assertEquals(true, run.configuration().includeRed());
        assertEquals("ollama", run.configuration().provider());
        assertEquals("development", run.configuration().datasetSplit());
        assertTrue(run.datasetHash() != null && run.datasetHash().length() == 64);
        assertEquals(GoldenReader.datasetHash(), run.datasetHash());
        assertEquals(64, run.packHash().length());
        assertEquals(eval.pack.PackFiles.contentHash(), run.packHash());
    }

    @Test
    void committedHistoryRecordsNightlyJudgeModel() throws Exception {
        EvalRun nightly = ReportIo.readRun(Path.of("baselines/nightly-generation-v1.json"));
        boolean found = false;
        for (String line : Files.readAllLines(Path.of("baselines/history.jsonl"))) {
            if (!line.contains("\"runId\":\"" + nightly.runId() + "\"")) {
                continue;
            }
            found = true;
            assertTrue(
                    line.contains("\"judgeModel\":\"" + EvalConfig.DEFAULT_JUDGE_MODEL + "\""),
                    line);
        }
        assertTrue(found, "history.jsonl must record nightly run " + nightly.runId());
    }

    @Test
    void saveBaselineRefusesOverwriteWithoutForce() {
        int code = eval.cli.EvalMain.run(new String[]{
                "--mode=deterministic",
                "--save-baseline=baselines/generation-v1.json",
                "--artifacts=never",
                "--output=build/eval-save-guard"
        });
        assertEquals(eval.cli.ExitCode.USAGE, code);
    }

    @Test
    void saveBaselineRefusesInfrastructureErrorRun() {
        Path dest = Path.of("build/eval-save-guard-error/snap.json");
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=live",
                "--judge=false",
                "--red",
                "--artifacts=never",
                "--output=build/eval-save-guard-error",
                "--save-baseline=" + dest,
                "--force-save-baseline"
        });
        eval.provider.ModelRunner runner = (system, user, model) -> {
            throw new eval.provider.EvalInfrastructureException(
                    eval.provider.EvalInfrastructureException.MODEL_UNAVAILABLE, "ollama down");
        };
        EvalRun run = new eval.execution.EvalExecutor(config, runner).execute();
        assertEquals(8, run.casesError());
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> ArtifactWriter.write(run, config));
        assertTrue(ex.getMessage().contains("infrastructure errors"));
        assertTrue(!java.nio.file.Files.isRegularFile(dest));
    }
}
