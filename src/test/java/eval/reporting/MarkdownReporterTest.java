package eval.reporting;

import eval.domain.EvalRun;
import eval.execution.EvalConfig;
import eval.execution.EvalExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Reports")
class MarkdownReporterTest {

    @TempDir
    Path tmp;

    @Test
    void reportContainsIdentityMetricsAndGate() {
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=deterministic", "--gate", "--output=" + tmp, "--artifacts=never", "--experiment=prompt-v13"
        });
        EvalRun run = new EvalExecutor(config).execute();
        String md = MarkdownReporter.render(run, null);
        assertTrue(md.contains("prompt-v13"));
        assertTrue(md.contains("100.0%"));
        assertTrue(md.contains("Coverage"));
        assertTrue(md.contains("Quality gate"));
        assertTrue(md.contains(run.datasetHash()));
        assertTrue(md.contains("Hallucination (fail rate)"));
        assertTrue(md.contains("Slice hallucination (pass rate)"));
        String console = ConsoleReporter.render(run, null);
        assertTrue(console.contains("Pass rate:"));
        assertTrue(console.contains("Coverage:"));
        assertTrue(console.contains("Hallucination (fail rate)"));
        assertTrue(console.contains("Slice hallucination (pass rate)"));
        ArtifactWriter.write(run, config);
        assertTrue(Files.isRegularFile(tmp.resolve("history.jsonl")));
    }

    @Test
    void emptyMetricsRenderAsNa() {
        String md = MarkdownReporter.render(
                new EvalExecutor(EvalConfig.resolve(new String[]{
                        "--mode=deterministic", "--output=" + tmp, "--artifacts=never"
                })).execute(),
                eval.domain.ComparisonResult.invalid("COMPARISON INVALID: demo"));
        assertTrue(md.contains("COMPARISON INVALID"));
    }
}
