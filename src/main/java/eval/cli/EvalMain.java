package eval.cli;

import eval.comparison.RunComparator;
import eval.domain.ComparisonResult;
import eval.domain.EvalRun;
import eval.execution.EvalConfig;
import eval.execution.EvalExecutor;
import eval.generation.JudgeCalibration;
import eval.provider.EvalInfrastructureException;
import eval.provider.ModelRunners;
import eval.reporting.ArtifactWriter;
import eval.reporting.ConsoleReporter;
import eval.reporting.MarkdownReporter;
import eval.reporting.ReportIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EvalMain {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        if (has(args, "--help") || has(args, "-h")) {
            System.out.println(help());
            return ExitCode.SUCCESS;
        }
        if (has(args, "--calibrate-judge")) {
            return runCalibration(args);
        }
        EvalConfig config;
        try {
            config = EvalConfig.resolve(args);
        } catch (RuntimeException e) {
            System.err.println("USAGE: " + e.getMessage());
            return ExitCode.USAGE;
        }
        if (config.saveBaselinePath() != null
                && Files.isRegularFile(config.saveBaselinePath())
                && !config.forceSaveBaseline()) {
            System.err.println(
                    "USAGE: " + config.saveBaselinePath()
                            + " already exists. Pass --force-save-baseline (or -DforceSaveBaseline=true) to overwrite.");
            return ExitCode.USAGE;
        }
        try {
            return switch (config.mode()) {
                case DETERMINISTIC, LIVE -> runOnce(config, null);
                case REGRESSION -> runRegression(config);
                case BENCHMARK -> runBenchmark(config);
            };
        } catch (IllegalArgumentException e) {
            System.err.println("USAGE: " + e.getMessage());
            return ExitCode.USAGE;
        } catch (RuntimeException e) {
            System.err.println("EVAL failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return ExitCode.USAGE;
        }
    }

    static int runCalibration(String[] args) {
        EvalConfig config;
        try {
            config = EvalConfig.resolve(args);
        } catch (RuntimeException e) {
            System.err.println("USAGE: " + e.getMessage());
            return ExitCode.USAGE;
        }
        try {
            var rows = JudgeCalibration.load(JudgeCalibration.defaultFile());
            boolean live = has(args, "--live") || config.usesModel();
            JudgeCalibration.Report report = live
                    ? JudgeCalibration.evaluateLive(rows, ModelRunners.create(config), config.judgeModel())
                    : JudgeCalibration.evaluate(rows);
            Path dir = config.outputDir().resolve("calibration");
            ReportIo.writeJson(dir.resolve("report.json"), report);
            Files.writeString(dir.resolve("report.md"), JudgeCalibration.render(report));
            System.out.print(JudgeCalibration.render(report));
            System.out.println("Wrote " + dir.toAbsolutePath());
            return ExitCode.SUCCESS;
        } catch (EvalInfrastructureException e) {
            System.err.println("CALIBRATION infra: " + e.kind() + " " + e.getMessage());
            return ExitCode.INFRASTRUCTURE_FAILURE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("CALIBRATION interrupted");
            return ExitCode.INFRASTRUCTURE_FAILURE;
        } catch (Exception e) {
            System.err.println("CALIBRATION failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return ExitCode.USAGE;
        }
    }

    static int runOnce(EvalConfig config, EvalRun baseline) {
        EvalRun run = new EvalExecutor(config).execute();
        Path dir = ArtifactWriter.write(run, config);
        ComparisonResult comparison = null;
        if (baseline != null) {
            comparison = RunComparator.compare(baseline, run, config.thresholds());
            ReportIo.writeJson(dir.resolve("comparison.json"), comparison);
            Path report = dir.resolve("eval-report.md");
            try {
                Files.writeString(report, MarkdownReporter.render(run, comparison));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        System.out.print(ConsoleReporter.render(run, comparison));
        System.out.println("Wrote " + dir.toAbsolutePath());
        return exit(run, comparison);
    }

    static int runRegression(EvalConfig config) {
        if (config.baselinePath() == null || !Files.isRegularFile(config.baselinePath())) {
            System.err.println("REGRESSION requires --baseline=path to an existing run JSON");
            return ExitCode.USAGE;
        }
        EvalRun baseline = ReportIo.readRun(config.baselinePath());
        if (config.usesModel() && !EvalExecutor.isModelSnapshot(baseline)) {
            System.err.println(
                    "COMPARISON INVALID: live regression needs a live baseline, not a fixture snapshot");
            return ExitCode.COMPARISON_INVALID;
        }
        if (config.usesModel()) {
            String protocol = RunComparator.protocolMismatch(
                    baseline, config.repetitions(), config.includeRed());
            if (protocol != null) {
                System.err.println(protocol);
                return ExitCode.COMPARISON_INVALID;
            }
        }
        if (config.candidatePath() != null) {
            return compareExisting(config, baseline);
        }
        return runOnce(config, baseline);
    }

    static int compareExisting(EvalConfig config, EvalRun baseline) {
        Path path = config.candidatePath();
        if (path == null || !Files.isRegularFile(path)) {
            System.err.println("REGRESSION --candidate must be an existing run.json");
            return ExitCode.USAGE;
        }
        EvalRun run = ReportIo.readRun(path);
        ComparisonResult comparison = RunComparator.compare(baseline, run, config.thresholds());
        Path dir = config.outputDir().resolve(run.runId());
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ReportIo.writeJson(dir.resolve("comparison.json"), comparison);
        try {
            Files.writeString(dir.resolve("eval-report.md"), MarkdownReporter.render(run, comparison));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.print(ConsoleReporter.render(run, comparison));
        System.out.println("Compared " + path.toAbsolutePath() + " vs " + config.baselinePath());
        System.out.println("Wrote " + dir.toAbsolutePath());
        return exit(run, comparison);
    }

    static int runBenchmark(EvalConfig config) {
        List<String> models = config.benchmarkModels();
        if (models.isEmpty()) {
            models = List.of(config.model());
        }
        List<EvalRun> runs = new ArrayList<>();
        Path root = config.outputDir().resolve("benchmark");
        for (String model : models) {
            EvalConfig one = config.copyForModel(model);
            EvalRun run = new EvalExecutor(one).execute();
            ArtifactWriter.write(run, one);
            runs.add(run);
        }
        ReportIo.writeJson(root.resolve("benchmark.json"), runs.stream().map(eval.reporting.SummaryView::of).toList());
        if (runs.size() >= 2) {
            ComparisonResult comparison = RunComparator.compare(runs.get(0), runs.get(1), config.thresholds());
            ReportIo.writeJson(root.resolve("comparison.json"), comparison);
            System.out.print(ConsoleReporter.render(runs.get(1), comparison));
        }
        System.out.print(ConsoleReporter.renderBenchmark(runs));
        int code = ExitCode.SUCCESS;
        for (EvalRun run : runs) {
            code = worse(code, exit(run, null));
        }
        return code;
    }

    public static int exit(EvalRun run, ComparisonResult comparison) {
        if (comparison != null && !comparison.valid()) {
            return ExitCode.COMPARISON_INVALID;
        }
        if (run.casesTotal() == 0) {
            System.err.println("EVAL: empty dataset");
            return ExitCode.QUALITY_GATE_FAILED;
        }
        if (run.hasNoQualityAttempts() && run.attemptsError() == 0) {
            System.err.println("EVAL: no quality attempts (all skipped or empty) — not a pass");
            return ExitCode.QUALITY_GATE_FAILED;
        }
        if (run.attemptsError() > 0 && run.qualityAttempts() == 0) {
            return ExitCode.INFRASTRUCTURE_FAILURE;
        }
        if (run.casesError() > 0 && run.casesPassed() + run.casesFailed() == 0) {
            return ExitCode.INFRASTRUCTURE_FAILURE;
        }
        var gate = comparison != null && comparison.qualityGate() != null
                ? comparison.qualityGate()
                : run.qualityGate();
        if (gate != null && !gate.passed()) {
            return ExitCode.QUALITY_GATE_FAILED;
        }
        if (run.attemptsError() > 0) {
            return ExitCode.INFRASTRUCTURE_FAILURE;
        }
        return ExitCode.SUCCESS;
    }

    private static int worse(int a, int b) {
        return Math.max(a, b);
    }

    private static boolean has(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    static String help() {
        return """
                java-ai-golden eval

                  --mode=deterministic|live|benchmark|regression
                  --model=NAME
                  --judgeModel=NAME
                  --judge=true|false
                  --repetitions=N
                  --output=DIR
                  --baseline=PATH
                  --candidate=PATH
                  --save-baseline=PATH
                  --force-save-baseline
                  --models=a,b,c
                  --red
                  --live
                  --gate
                  --artifacts=failure|always|never
                  --provider=ollama|openai
                  --openaiBaseUrl=URL
                  --openaiApiKey=KEY
                  --experiment=ID
                  --split=development|holdout
                  --judge-repetitions=N
                  --calibrate-judge [--live]
                  --config=eval.json

                Exit codes:
                  0 SUCCESS
                  1 USAGE
                  2 QUALITY_GATE_FAILED
                  3 INFRASTRUCTURE_FAILURE (MODEL_UNAVAILABLE, …)
                  4 COMPARISON_INVALID
                """;
    }
}
