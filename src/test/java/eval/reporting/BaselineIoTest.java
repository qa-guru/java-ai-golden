package eval.reporting;

import eval.domain.EvalRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Baseline JSON")
class BaselineIoTest {

    @Test
    void committedDeterministicBaselineIsReadable() {
        EvalRun run = ReportIo.readRun(Path.of("baselines/generation-v1.json"));
        assertEquals("generation-v1", run.datasetVersion());
        assertEquals(8, run.casesTotal());
        assertEquals(8, run.casesPassed());
        assertTrue(run.metrics().overallPassRate().defined());
        assertEquals(1.0, run.metrics().overallPassRate().value(), 1e-12);
    }
}
