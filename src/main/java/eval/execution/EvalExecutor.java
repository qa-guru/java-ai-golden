package eval.execution;

import eval.dataset.DatasetIdentity;
import eval.domain.AttemptResult;
import eval.domain.CaseFlags;
import eval.domain.CaseResult;
import eval.domain.ContractResult;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;
import eval.domain.GateRuleResult;
import eval.domain.QualityGateResult;
import eval.domain.Rate;
import eval.domain.RetrievalResult;
import eval.domain.RunConfiguration;
import eval.domain.Violation;
import eval.generation.GoldenCase;
import eval.generation.GoldenReader;
import eval.generation.Judge;
import eval.generation.JudgeConsistency;
import eval.generation.WorkflowPrompt;
import eval.grading.ContractGrader;
import eval.grading.HardSoftPolicy;
import eval.grading.RetrievalGrader;
import eval.metrics.MetricsAggregator;
import eval.provider.EvalInfrastructureException;
import eval.provider.ModelResponse;
import eval.provider.ModelRunner;
import eval.provider.ModelRunners;
import eval.comparison.QualityGate;
import eval.comparison.RunComparator;
import eval.reporting.ReportIo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EvalExecutor {

    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private final EvalConfig config;
    private final ModelRunner runner;

    public EvalExecutor(EvalConfig config) {
        this(config, ModelRunners.create(config));
    }

    public EvalExecutor(EvalConfig config, ModelRunner runner) {
        this.config = config;
        this.runner = runner;
    }

    public EvalRun execute() {
        long started = System.nanoTime();
        String runId = LocalDateTime.now().format(RUN_ID);
        String timestamp = java.time.Instant.now().toString();
        List<GoldenCase> goldens = GoldenReader.loadSplit(config.datasetSplit());
        String datasetHash = DatasetIdentity.hash(goldens);
        List<CaseResult> cases = new ArrayList<>();
        for (GoldenCase row : goldens) {
            cases.add(executeCase(row));
        }
        var metrics = MetricsAggregator.aggregate(cases, config.weights());
        int casesPassed = 0;
        int casesFailed = 0;
        int casesSkipped = 0;
        int casesError = 0;
        int attemptsTotal = 0;
        int attemptsPassed = 0;
        int attemptsFailed = 0;
        int attemptsSkipped = 0;
        int attemptsError = 0;
        for (CaseResult cse : cases) {
            switch (cse.status()) {
                case PASS -> casesPassed++;
                case FAIL -> casesFailed++;
                case SKIPPED -> casesSkipped++;
                case ERROR -> casesError++;
            }
            for (AttemptResult a : cse.attempts()) {
                attemptsTotal++;
                switch (a.status()) {
                    case PASS -> attemptsPassed++;
                    case FAIL -> attemptsFailed++;
                    case ERROR -> attemptsError++;
                    case SKIPPED -> attemptsSkipped++;
                }
            }
        }
        String executionMode = config.usesModel() ? "LIVE" : "DETERMINISTIC";
        RunConfiguration configuration = new RunConfiguration(
                executionMode,
                config.model(),
                config.judgeModel(),
                config.judgeEnabled(),
                config.repetitions(),
                config.includeRed(),
                config.artifactMode().name(),
                config.outputDir().toString(),
                config.provider(),
                config.experimentId(),
                config.datasetSplit(),
                RunConfiguration.currentJavaVersion());
        String gitCommit = GitMetadata.shortCommit();
        String packHash = config.packHash();
        String fingerprint = ConfigFingerprint.of(
                configuration,
                config.datasetVersion(),
                datasetHash,
                config.packDatasetVersion(),
                config.experimentId(),
                gitCommit,
                packHash);
        long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
        EvalRun draft = new EvalRun(
                runId,
                timestamp,
                config.model(),
                config.judgeEnabled() ? config.judgeModel() : null,
                config.datasetVersion(),
                config.packDatasetVersion(),
                datasetHash,
                packHash,
                gitCommit,
                config.experimentId(),
                fingerprint,
                configuration,
                goldens.size(),
                casesPassed,
                casesFailed,
                casesSkipped,
                casesError,
                attemptsTotal,
                attemptsPassed,
                attemptsFailed,
                attemptsSkipped,
                attemptsError,
                metrics,
                cases,
                durationMs,
                null);
        QualityGateResult gate = config.applyGate() ? resolveGate(draft) : null;
        return draft.withQualityGate(gate);
    }

    private QualityGateResult resolveGate(EvalRun draft) {
        EvalRun baseline = loadBaselineIfPresent(config);
        if (config.usesModel()) {
            if (baseline == null) {
                return QualityGate.unusableBaseline("live quality gate needs a live baseline file");
            }
            if (!isModelSnapshot(baseline)) {
                return QualityGate.unusableBaseline(
                        "live quality gate needs a live baseline, not a fixture snapshot");
            }
            var probe = RunComparator.compare(baseline, draft, null);
            if (!probe.valid()) {
                return QualityGate.unusableBaseline(probe.invalidReason());
            }
            return QualityGate.evaluate(draft, config.thresholds(), baseline);
        }
        if (baseline == null) {
            return QualityGate.evaluate(draft, config.thresholds(), null);
        }
        var probe = RunComparator.compare(baseline, draft, null);
        if (!probe.valid()) {
            QualityGateResult absolute = QualityGate.evaluate(draft, config.thresholds(), null);
            ArrayList<GateRuleResult> rules = new ArrayList<>();
            rules.add(new GateRuleResult(
                    "baseline",
                    "ignored incompatible baseline (" + probe.invalidReason()
                            + "); absolute thresholds only",
                    true,
                    null,
                    null,
                    "execution"));
            rules.addAll(absolute.rules());
            return absolute.passed() ? QualityGateResult.pass(rules) : QualityGateResult.fail(rules);
        }
        return QualityGate.evaluate(draft, config.thresholds(), baseline);
    }

    public static boolean isModelSnapshot(EvalRun run) {
        if (run == null || run.configuration() == null || run.configuration().mode() == null) {
            return false;
        }
        return !"DETERMINISTIC".equals(run.configuration().mode());
    }

    private static EvalRun loadBaselineIfPresent(EvalConfig config) {
        if (config.baselinePath() == null || !Files.isRegularFile(config.baselinePath())) {
            return null;
        }
        return ReportIo.readRun(config.baselinePath());
    }

    CaseResult executeCase(GoldenCase row) {
        RetrievalResult retrieval = RetrievalGrader.grade(row);
        if (config.usesModel() && row.expect().isRed() && !config.includeRed()) {
            AttemptResult skipped = AttemptResult.skipped(1, "red row: pass --red or -Dred=true");
            return assemble(row, List.of(skipped), retrieval, EvalStatus.SKIPPED, 0);
        }
        if (!config.usesModel()) {
            return executeDeterministic(row, retrieval);
        }
        WorkflowPrompt.Built built;
        try {
            built = WorkflowPrompt.build(row);
        } catch (IllegalStateException e) {
            return livePromptError(row, retrieval, e.getMessage());
        }
        return executeLive(row, retrieval, built);
    }

    private CaseResult executeDeterministic(GoldenCase row, RetrievalResult retrieval) {
        long started = System.nanoTime();
        String fixture = GoldenReader.fixture(row.id());
        ContractResult contract = ContractGrader.grade(row, fixture);
        EvalStatus status = HardSoftPolicy.hardStatus(contract);
        if (retrieval.applicable() && !retrieval.passed()) {
            status = EvalStatus.FAIL;
        }
        long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
        AttemptResult attempt = new AttemptResult(
                1,
                status,
                contract,
                null,
                fixture,
                null,
                eval.domain.TokenUsage.unknown(),
                durationMs,
                null,
                status == EvalStatus.FAIL ? String.join("; ", contract.violations()) : null);
        return assemble(row, List.of(attempt), retrieval, status, durationMs);
    }

    private CaseResult livePromptError(GoldenCase row, RetrievalResult retrieval, String message) {
        AttemptResult attempt = AttemptResult.error(
                1, EvalInfrastructureException.RETRIEVER_MISS, message, 1L);
        return assemble(row, List.of(attempt), retrieval, EvalStatus.ERROR, 1L);
    }

    CaseResult executeLive(GoldenCase row, RetrievalResult retrieval, WorkflowPrompt.Built built) {
        if (!row.expect().refused() && (built == null || built.retrieved() == null || built.retrieved().isEmpty())) {
            return livePromptError(row, retrieval, row.id() + ": retriever returned no chunks");
        }
        List<AttemptResult> attempts = new ArrayList<>();
        long caseStarted = System.nanoTime();
        int n = config.repetitions();
        for (int i = 1; i <= n; i++) {
            attempts.add(liveAttempt(i, row, built));
        }
        if (config.writeFixtures() && !attempts.isEmpty() && attempts.getFirst().rawOutput() != null) {
            try {
                Path path = GoldenReader.writableEvalDir().resolve("fixtures").resolve(row.id() + ".out.md");
                Files.writeString(path, attempts.getFirst().rawOutput(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // mill flag is best-effort; eval status already recorded
            }
        }
        long durationMs = Math.max(1L, (System.nanoTime() - caseStarted) / 1_000_000L);
        EvalStatus status = rollup(attempts);
        if (status == EvalStatus.PASS && retrieval.applicable() && !retrieval.passed()) {
            status = EvalStatus.FAIL;
        }
        return assemble(row, attempts, retrieval, status, durationMs);
    }

    private AttemptResult liveAttempt(int index, GoldenCase row, WorkflowPrompt.Built built) {
        long started = System.nanoTime();
        try {
            ModelResponse response = runner.complete(built.system(), row.prompt(), config.model());
            ContractResult contract = ContractGrader.grade(row, response.content());
            EvalStatus status = HardSoftPolicy.hardStatus(contract);
            JudgeResult judge = null;
            String judgeRaw = null;
            Rate judgeStability = null;
            if (config.judgeEnabled() && !row.expect().refused()) {
                try {
                    int jn = config.judgeRepetitions();
                    List<JudgeDecision> decisions = new ArrayList<>();
                    for (int j = 0; j < jn; j++) {
                        judgeRaw = Judge.review(
                                row, response.content(), built.retrieved(), runner, config.judgeModel());
                        judge = Judge.parseResult(judgeRaw);
                        decisions.add(judge.decision());
                    }
                    if (jn > 1) {
                        judgeStability = JudgeConsistency.stability(decisions);
                    }
                    if (HardSoftPolicy.judgeOverrideAttempted(contract, judge)) {
                        status = EvalStatus.FAIL;
                    }
                } catch (EvalInfrastructureException e) {
                    return AttemptResult.error(
                            index,
                            EvalInfrastructureException.JUDGE_ERROR,
                            "judge: " + e.getMessage(),
                            elapsed(started));
                }
            }
            return new AttemptResult(
                    index,
                    status,
                    contract,
                    judge,
                    response.content(),
                    judgeRaw,
                    response.tokens(),
                    Math.max(response.durationMs(), elapsed(started)),
                    null,
                    status == EvalStatus.FAIL ? String.join("; ", contract.violations()) : null,
                    judgeStability);
        } catch (EvalInfrastructureException e) {
            return AttemptResult.error(index, e.kind(), e.getMessage(), elapsed(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AttemptResult.error(index, EvalInfrastructureException.PROVIDER_ERROR, "interrupted", elapsed(started));
        } catch (Exception e) {
            if (e instanceof java.io.IOException io && io.getCause() instanceof EvalInfrastructureException inf) {
                return AttemptResult.error(index, inf.kind(), inf.getMessage(), elapsed(started));
            }
            if (e instanceof java.io.IOException) {
                return AttemptResult.error(
                        index, EvalInfrastructureException.MODEL_UNAVAILABLE, e.getMessage(), elapsed(started));
            }
            return AttemptResult.error(index, EvalInfrastructureException.PROVIDER_ERROR, e.getMessage(), elapsed(started));
        }
    }

    private static long elapsed(long startedNanos) {
        return Math.max(1L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    static EvalStatus rollup(List<AttemptResult> attempts) {
        boolean anyQuality = false;
        boolean allPass = true;
        boolean anyError = false;
        boolean allSkipped = true;
        for (AttemptResult a : attempts) {
            if (a.status() != EvalStatus.SKIPPED) {
                allSkipped = false;
            }
            if (a.status() == EvalStatus.ERROR) {
                anyError = true;
            }
            if (a.quality()) {
                anyQuality = true;
                if (a.status() != EvalStatus.PASS) {
                    allPass = false;
                }
            }
        }
        if (allSkipped) {
            return EvalStatus.SKIPPED;
        }
        if (anyError && !anyQuality) {
            return EvalStatus.ERROR;
        }
        if (anyError && allPass) {
            return EvalStatus.ERROR;
        }
        if (anyQuality && allPass) {
            return EvalStatus.PASS;
        }
        if (anyQuality) {
            return EvalStatus.FAIL;
        }
        return EvalStatus.ERROR;
    }

    private static CaseResult assemble(
            GoldenCase row,
            List<AttemptResult> attempts,
            RetrievalResult retrieval,
            EvalStatus status,
            long durationMs) {
        int passed = 0;
        int quality = 0;
        ContractResult lastContract = null;
        JudgeResult lastJudge = null;
        List<String> errors = new ArrayList<>();
        for (AttemptResult a : attempts) {
            if (a.quality()) {
                quality++;
                if (a.status() == EvalStatus.PASS) {
                    passed++;
                }
            }
            if (a.contract() != null) {
                lastContract = a.contract();
            }
            if (a.judge() != null) {
                lastJudge = a.judge();
            }
            if (a.errorKind() != null) {
                errors.add(a.errorKind() + ": " + a.errorMessage());
            }
        }
        Rate success = Rate.of(passed, quality);
        List<Violation> taxonomy = lastContract == null ? List.of() : lastContract.taxonomy();
        Rate judgeStability = null;
        if (attempts.size() == 1) {
            judgeStability = attempts.getFirst().judgeStability();
        } else {
            List<JudgeDecision> all = new ArrayList<>();
            for (AttemptResult a : attempts) {
                if (a.judge() != null) {
                    all.add(a.judge().decision());
                }
            }
            if (all.size() > 1) {
                judgeStability = JudgeConsistency.stability(all);
            }
        }
        return new CaseResult(
                row.id(),
                status,
                CaseFlags.of(row),
                attempts,
                lastContract,
                lastJudge,
                retrieval,
                success,
                errors,
                durationMs,
                Map.of("prompt", row.prompt()),
                taxonomy,
                judgeStability);
    }
}
