package eval.reporting;

import eval.cli.ExitCode;
import eval.cli.PagesMain;
import eval.domain.EvalRun;
import eval.execution.EvalConfig;
import eval.execution.EvalExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("GitHub Pages site")
class PagesSiteTest {

    @TempDir
    Path tmp;

    @Test
    void htmlRendersHeadingsTablesAndInlineMarkup() {
        String html = MarkdownHtml.toHtml("""
                # AI eval report

                - Run: `abc`
                - **PASS**
                - _None._

                | metric | value |
                |---|---|
                | Overall | 100.0% |
                """);
        assertTrue(html.contains("<h1>AI eval report</h1>"));
        assertTrue(html.contains("<code>abc</code>"));
        assertTrue(html.contains("<strong>PASS</strong>"));
        assertTrue(html.contains("<em>None.</em>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>metric</th>"));
        assertTrue(html.contains("<td>Overall</td>"));
        assertTrue(html.contains("&") || html.contains("100.0%"));
    }

    @Test
    void htmlEscapesAngleBracketsOutsideCode() {
        assertTrue(MarkdownHtml.toHtml("see List<String>").contains("List&lt;String&gt;"));
    }

    @Test
    void prefersComparedRunOverNewerBareRun() throws Exception {
        Path evalDir = tmp.resolve("eval");
        EvalRun seed = execute("seed");
        Path seedDir = evalDir.resolve(seed.runId());
        cloneRun(seedDir, evalDir.resolve("run-old"), seed, "run-old", "2020-01-01T00:00:00Z", true);
        cloneRun(seedDir, evalDir.resolve("run-new"), seed, "run-new", "2026-01-01T00:00:00Z", false);
        deleteRecursively(seedDir);
        Path site = tmp.resolve("site");
        PagesSite.write(evalDir, site);
        String index = Files.readString(site.resolve("index.html"));
        assertTrue(index.contains("run-old"));
        assertFalse(index.contains("run-new"));
        assertTrue(Files.isRegularFile(site.resolve(".nojekyll")));
        assertTrue(Files.isRegularFile(site.resolve("development/index.html")));
        assertTrue(Files.isRegularFile(site.resolve("development/eval-report.md")));
        String report = Files.readString(site.resolve("development/index.html"));
        assertTrue(report.contains("java-ai-golden"));
        assertTrue(report.contains("100.0%"));
    }

    @Test
    void holdoutAndCalibrationGetTheirOwnSlots() throws Exception {
        Path evalDir = tmp.resolve("eval");
        executeHoldout();
        Files.createDirectories(evalDir.resolve("calibration"));
        Files.writeString(evalDir.resolve("calibration").resolve("report.md"), "# Judge calibration\n\n- n: 4\n");
        Path site = tmp.resolve("site");
        PagesSite.write(evalDir, site);
        String index = Files.readString(site.resolve("index.html"));
        assertTrue(index.contains("Holdout"));
        assertTrue(index.contains("Judge calibration"));
        assertTrue(Files.isRegularFile(site.resolve("holdout/index.html")));
        assertTrue(Files.isRegularFile(site.resolve("calibration/index.html")));
    }

    @Test
    void missingEvalDirIsUsage() throws Exception {
        assertEquals(ExitCode.USAGE, PagesMain.run(new String[]{
                "--input=" + tmp.resolve("missing"),
                "--output=" + tmp.resolve("out")
        }));
        assertThrows(IllegalArgumentException.class, () -> PagesSite.write(tmp.resolve("empty"), tmp.resolve("site")));
        Files.createDirectories(tmp.resolve("empty"));
        assertEquals(ExitCode.USAGE, PagesMain.run(new String[]{
                "--input=" + tmp.resolve("empty"),
                "--output=" + tmp.resolve("out")
        }));
    }

    private EvalRun execute(String experiment) {
        Path evalDir = tmp.resolve("eval");
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=deterministic",
                "--gate",
                "--output=" + evalDir,
                "--artifacts=never",
                "--experiment=" + experiment
        });
        EvalRun run = new EvalExecutor(config).execute();
        ArtifactWriter.write(run, config);
        return run;
    }

    private void executeHoldout() {
        Path evalDir = tmp.resolve("eval");
        EvalConfig config = EvalConfig.resolve(new String[]{
                "--mode=deterministic",
                "--split=holdout",
                "--gate",
                "--output=" + evalDir,
                "--artifacts=never"
        });
        EvalRun run = new EvalExecutor(config).execute();
        ArtifactWriter.write(run, config);
        assertEquals("holdout", PagesSite.slotOf(run));
    }

    private void cloneRun(
            Path source,
            Path dest,
            EvalRun seed,
            String runId,
            String timestamp,
            boolean compared) throws Exception {
        Files.createDirectories(dest);
        EvalRun copy = new EvalRun(
                runId,
                timestamp,
                seed.model(),
                seed.judgeModel(),
                seed.datasetVersion(),
                seed.packDatasetVersion(),
                seed.datasetHash(),
                seed.packHash(),
                seed.gitCommit(),
                seed.experimentId(),
                seed.configFingerprint(),
                seed.configuration(),
                seed.casesTotal(),
                seed.casesPassed(),
                seed.casesFailed(),
                seed.casesSkipped(),
                seed.casesError(),
                seed.attemptsTotal(),
                seed.attemptsPassed(),
                seed.attemptsFailed(),
                seed.attemptsSkipped(),
                seed.attemptsError(),
                seed.metrics(),
                seed.cases(),
                seed.durationMs(),
                seed.qualityGate());
        ReportIo.writeJson(dest.resolve("run.json"), ArtifactWriter.slim(copy));
        Files.copy(source.resolve("eval-report.md"), dest.resolve("eval-report.md"));
        if (compared) {
            Files.writeString(dest.resolve("comparison.json"), "{\"valid\":true}");
        }
    }

    private static void deleteRecursively(Path dir) throws Exception {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
