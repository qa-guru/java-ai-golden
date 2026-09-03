package eval.generation;

import eval.pack.LexicalRetriever;
import eval.cli.EvalMain;
import eval.cli.ExitCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Holdout split")
class HoldoutDatasetTest {

    @TempDir
    Path tmp;

    @Test
    void holdoutIsSeparateFromDevelopment() {
        assertEquals("holdout-v1", GoldenReader.datasetVersion("holdout"));
        assertEquals(8, GoldenReader.loadHoldout().size());
        assertEquals(8, GoldenReader.loadAll().size());
        var devIds = GoldenReader.loadAll().stream().map(GoldenCase::id).toList();
        for (GoldenCase row : GoldenReader.loadHoldout()) {
            assertTrue(!devIds.contains(row.id()), row.id());
        }
    }

    @Test
    void holdoutRetrieverMatchesOracle() {
        for (GoldenCase row : GoldenReader.loadHoldout()) {
            if (row.expect().refused()) {
                continue;
            }
            List<String> got = LexicalRetriever.retrieve(row.prompt());
            assertEquals(new HashSet<>(row.expect().rag()), new HashSet<>(got), row.id() + " got=" + got);
        }
    }

    @Test
    void holdoutFixturesPassContract() {
        eval.execution.EvalConfig config = eval.execution.EvalConfig.resolve(new String[]{
                "--mode=deterministic",
                "--split=holdout",
                "--gate",
                "--baseline=baselines/holdout-v1.json",
                "--artifacts=never",
                "--output=build/eval-holdout-test"
        });
        eval.domain.EvalRun run = new eval.execution.EvalExecutor(config).execute();
        assertEquals("holdout-v1", run.datasetVersion());
        assertEquals(8, run.casesTotal());
        assertEquals(8, run.casesPassed());
        assertEquals(1.0, run.metrics().overallPassRate().value(), 1e-12);
        assertTrue(run.qualityGate() != null && run.qualityGate().passed());
    }

    @Test
    void holdoutRegressionCliMatchesCommittedBaseline() {
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--split=holdout",
                "--gate",
                "--baseline=baselines/holdout-v1.json",
                "--artifacts=never",
                "--output=" + tmp
        });
        assertEquals(ExitCode.SUCCESS, code);
    }
}
