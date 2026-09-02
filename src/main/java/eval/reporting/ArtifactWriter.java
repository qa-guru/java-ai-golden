package eval.reporting;

import eval.domain.AttemptResult;
import eval.domain.CaseResult;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.execution.ArtifactMode;
import eval.execution.EvalConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ArtifactWriter {

    private ArtifactWriter() {
    }

    public static Path write(EvalRun run, EvalConfig config) {
        Path dir = config.outputDir().resolve(run.runId());
        try {
            Files.createDirectories(dir.resolve("cases"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        EvalRun slim = slim(run);
        ReportIo.writeJson(dir.resolve("run.json"), slim);
        ReportIo.writeJson(dir.resolve("summary.json"), SummaryView.of(run));
        writeString(dir.resolve("eval-report.md"), MarkdownReporter.render(run, null));
        EvalHistory.append(config.outputDir().resolve("history.jsonl"), slim);
        for (CaseResult cse : run.cases()) {
            boolean failed = cse.status() == EvalStatus.FAIL || cse.status() == EvalStatus.ERROR;
            boolean writeCase = config.artifactMode() == ArtifactMode.ALWAYS
                    || (config.artifactMode() == ArtifactMode.FAILURE && failed);
            if (!writeCase) {
                continue;
            }
            Path caseDir = dir.resolve("cases").resolve(cse.caseId());
            ReportIo.writeJson(caseDir.resolve("result.json"), cse);
            AttemptResult lastQuality = lastQuality(cse);
            if (lastQuality != null && lastQuality.rawOutput() != null) {
                writeString(caseDir.resolve("output.md"), lastQuality.rawOutput());
            }
            if (lastQuality != null && lastQuality.judgeOutput() != null) {
                writeString(caseDir.resolve("judge.md"), lastQuality.judgeOutput());
            }
            if (cse.attempts().size() > 1) {
                for (AttemptResult attempt : cse.attempts()) {
                    Path ad = caseDir.resolve("attempts").resolve(String.valueOf(attempt.index()));
                    ReportIo.writeJson(ad.resolve("result.json"), attempt);
                    if (attempt.rawOutput() != null) {
                        writeString(ad.resolve("output.md"), attempt.rawOutput());
                    }
                    if (attempt.judgeOutput() != null) {
                        writeString(ad.resolve("judge.md"), attempt.judgeOutput());
                    }
                }
            }
        }
        if (config.saveBaselinePath() != null) {
            if (Files.isRegularFile(config.saveBaselinePath()) && !config.forceSaveBaseline()) {
                throw new IllegalStateException(
                        config.saveBaselinePath()
                                + " already exists; pass --force-save-baseline to overwrite");
            }
            ReportIo.writeJson(config.saveBaselinePath(), slim);
        }
        try {
            Files.writeString(
                    config.outputDir().resolve("LATEST"),
                    dir.toAbsolutePath() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    static EvalRun slim(EvalRun run) {
        List<CaseResult> slimCases = new ArrayList<>();
        for (CaseResult cse : run.cases()) {
            List<AttemptResult> attempts = new ArrayList<>();
            for (AttemptResult a : cse.attempts()) {
                attempts.add(new AttemptResult(
                        a.index(),
                        a.status(),
                        a.contract(),
                        a.judge() == null ? null : new eval.domain.JudgeResult(
                                a.judge().decision(),
                                a.judge().score(),
                                a.judge().reasons(),
                                a.judge().schemaValid(),
                                null),
                        null,
                        null,
                        a.tokens(),
                        a.durationMs(),
                        a.errorKind(),
                        a.errorMessage(),
                        a.judgeStability()));
            }
            slimCases.add(new CaseResult(
                    cse.caseId(),
                    cse.status(),
                    cse.kinds(),
                    attempts,
                    cse.contract(),
                    cse.judge() == null ? null : new eval.domain.JudgeResult(
                            cse.judge().decision(),
                            cse.judge().score(),
                            cse.judge().reasons(),
                            cse.judge().schemaValid(),
                            null),
                    cse.retrieval(),
                    cse.successRate(),
                    cse.errors(),
                    cse.durationMs(),
                    cse.metadata(),
                    cse.taxonomy(),
                    cse.judgeStability()));
        }
        return new EvalRun(
                run.runId(),
                run.timestamp(),
                run.model(),
                run.judgeModel(),
                run.datasetVersion(),
                run.packDatasetVersion(),
                run.datasetHash(),
                run.gitCommit(),
                run.experimentId(),
                run.configFingerprint(),
                run.configuration(),
                run.casesTotal(),
                run.casesPassed(),
                run.casesFailed(),
                run.casesSkipped(),
                run.casesError(),
                run.attemptsTotal(),
                run.attemptsPassed(),
                run.attemptsFailed(),
                run.attemptsSkipped(),
                run.attemptsError(),
                run.metrics(),
                slimCases,
                run.durationMs(),
                run.qualityGate());
    }

    private static AttemptResult lastQuality(CaseResult cse) {
        AttemptResult last = null;
        for (AttemptResult a : cse.attempts()) {
            if (a.rawOutput() != null || a.quality() || a.status() == EvalStatus.ERROR) {
                last = a;
            }
        }
        return last;
    }

    private static void writeString(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
