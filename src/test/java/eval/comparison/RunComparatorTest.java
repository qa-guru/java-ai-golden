package eval.comparison;

import eval.domain.AttemptResult;
import eval.domain.CaseKind;
import eval.domain.CaseRegression;
import eval.domain.CaseResult;
import eval.domain.ComparisonResult;
import eval.domain.ContractResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;
import eval.domain.QualityGateResult;
import eval.domain.Rate;
import eval.domain.RunConfiguration;
import eval.domain.Thresholds;
import eval.domain.TokenUsage;
import eval.metrics.MetricsAggregator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Comparison and quality gate")
class RunComparatorTest {

    @Test
    void repetitionsMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("1")), 1, false);
        EvalRun b = run("b", "generation-v1", List.of(passing("1")), 5, false);
        ComparisonResult result = RunComparator.compare(a, b);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("repetitions"));
    }

    @Test
    void includeRedMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("1")), 1, false);
        EvalRun b = run("b", "generation-v1", List.of(passing("1")), 1, true);
        ComparisonResult result = RunComparator.compare(a, b);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("includeRed"));
    }

    @Test
    void packVersionMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("GEN-001")));
        EvalRun b = EvalRun.of(
                "b",
                "t",
                "model-b",
                null,
                "generation-v1",
                "pack-v2",
                "abc",
                a.configuration(),
                1,
                1,
                0,
                0,
                0,
                1,
                1,
                0,
                0,
                a.metrics(),
                a.cases(),
                10,
                null);
        ComparisonResult result = RunComparator.compare(a, b);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("packDatasetVersion"));
    }

    @Test
    void datasetMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("GEN-001")));
        EvalRun b = run("b", "generation-v2", List.of(passing("GEN-001")));
        ComparisonResult result = RunComparator.compare(a, b);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("COMPARISON INVALID"));
    }

    @Test
    void perCaseRegressionEnums() {
        EvalRun baseline = run("base", "generation-v1", List.of(
                passing("GEN-001"),
                failing("GEN-002"),
                passing("GEN-014"),
                failing("GEN-003")));
        EvalRun candidate = run("cand", "generation-v1", List.of(
                passing("GEN-001"),
                failing("GEN-002"),
                failing("GEN-014"),
                passing("GEN-003")));
        ComparisonResult result = RunComparator.compare(baseline, candidate);
        assertTrue(result.valid());
        assertEquals(1, result.unchangedPass());
        assertEquals(1, result.unchangedFail());
        assertEquals(1, result.regressions());
        assertEquals(1, result.improvements());
        assertEquals(CaseRegression.UNCHANGED_PASS, find(result, "GEN-001"));
        assertEquals(CaseRegression.UNCHANGED_FAIL, find(result, "GEN-002"));
        assertEquals(CaseRegression.NEW_FAILURE, find(result, "GEN-014"));
        assertEquals(CaseRegression.RECOVERED, find(result, "GEN-003"));
    }

    @Test
    void absoluteThresholdFailsOverall() {
        EvalRun run = run("r", "generation-v1", List.of(passing("A"), failing("B")));
        QualityGateResult gate = QualityGate.evaluate(run, new Thresholds(
                0.90, null, null, null, null, null, null, null, null, null), null);
        assertFalse(gate.passed());
    }

    @Test
    void hallucinationAboveMaxFailsGate() {
        EvalRun run = run("r", "generation-v1", List.of(hallucinationFail("hallucinate-error")));
        QualityGateResult gate = QualityGate.evaluate(run, new Thresholds(
                null, null, null, null, null, null, null, null, 0.05, null), null);
        assertFalse(gate.passed());
    }

    @Test
    void judgeAcceptRateIsNotADeltaGate() {
        EvalRun baseline = run("b", "generation-v1", List.of(
                passingWithJudge("1", JudgeDecision.ACCEPT),
                passingWithJudge("2", JudgeDecision.ACCEPT),
                passingWithJudge("3", JudgeDecision.ACCEPT)));
        EvalRun candidate = run("c", "generation-v1", List.of(
                passingWithJudge("1", JudgeDecision.REJECT),
                passingWithJudge("2", JudgeDecision.REJECT),
                passingWithJudge("3", JudgeDecision.ACCEPT)));
        QualityGateResult gate = QualityGate.evaluate(candidate, Thresholds.liveDelta(), baseline);
        assertTrue(gate.passed());
        assertTrue(gate.rules().stream().noneMatch(r -> r.name().contains("judgeAcceptRate")));
    }

    @Test
    void absolutePassDoesNotWaiveDeltaRegression() {
        EvalRun baseline = run("b", "generation-v1", nCases(20, 20));
        EvalRun candidate = run("c", "generation-v1", nCases(5, 20));
        Thresholds t = new Thresholds(0.20, null, null, null, null, null, null, null, null, 0.02);
        QualityGateResult gate = QualityGate.evaluate(candidate, t, baseline);
        assertFalse(gate.passed());
        assertTrue(gate.rules().stream().anyMatch(r -> "absolute".equals(r.kind()) && r.passed()));
        assertTrue(gate.rules().stream().anyMatch(r -> "delta".equals(r.kind()) && !r.passed()));
        assertTrue(gate.rules().stream().anyMatch(r -> r.detail() != null && r.detail().contains("REGRESSION")));
    }

    @Test
    void packHashMismatchWhenBothPresentIsInvalid() {
        EvalRun a = withHashes(run("a", "generation-v1", List.of(passing("1"))), "ds", "pack-a");
        EvalRun b = withHashes(run("b", "generation-v1", List.of(passing("1"))), "ds", "pack-b");
        ComparisonResult result = RunComparator.compare(a, b);
        assertFalse(result.valid());
        assertEquals("COMPARISON_INVALID", result.decision());
        assertTrue(result.invalidReason().contains("packHash"));
    }

    @Test
    void missingPackHashVersusPresentIsStillValid() {
        EvalRun unhashed = withHashes(run("a", "generation-v1", List.of(passing("1"))), "ds", null);
        EvalRun hashed = withHashes(run("b", "generation-v1", List.of(passing("1"))), "ds", "pack-a");
        ComparisonResult result = RunComparator.compare(unhashed, hashed);
        assertTrue(result.valid());
    }

    @Test
    void dropBeyondAllowedFailsGate() {
        EvalRun baseline = run("b", "generation-v1", nCases(19, 20));
        EvalRun candidate = run("c", "generation-v1", nCases(18, 20));
        ComparisonResult result = RunComparator.compare(baseline, candidate, Thresholds.liveDelta());
        assertTrue(result.valid());
        assertEquals("REGRESSION", result.decision());
        assertFalse(result.qualityGate().passed());
        assertTrue(result.qualityGate().rules().stream()
                .anyMatch(r -> "delta".equals(r.kind()) && !r.passed() && r.detail().contains("REGRESSION")));
    }

    @Test
    void largeDropFailsGate() {
        EvalRun baseline = run("b", "generation-v1", nCases(20, 20));
        EvalRun candidate = run("c", "generation-v1", nCases(5, 20));
        ComparisonResult result = RunComparator.compare(baseline, candidate, Thresholds.liveDelta());
        assertTrue(result.valid());
        assertEquals("REGRESSION", result.decision());
        assertFalse(result.qualityGate().passed());
    }

    @Test
    void judgeMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("1")));
        EvalRun b = EvalRun.of(
                "b", "t", "model-b", "other-judge", "generation-v1", "pack-v1", "abc",
                new RunConfiguration("LIVE", "model-b", "other-judge", true, 1, false, "FAILURE", "build", "ollama"),
                1, 1, 0, 0, 0, 1, 1, 0, 0, a.metrics(), a.cases(), 10, null);
        EvalRun aLive = EvalRun.of(
                "a", "t", "model-a", "judge-a", "generation-v1", "pack-v1", "abc",
                new RunConfiguration("LIVE", "model-a", "judge-a", true, 1, false, "FAILURE", "build", "ollama"),
                1, 1, 0, 0, 0, 1, 1, 0, 0, a.metrics(), a.cases(), 10, null);
        ComparisonResult result = RunComparator.compare(aLive, b);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("judgeModel"));
    }

    @Test
    void datasetHashMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("1")));
        EvalRun hashedA = new EvalRun(
                a.runId(), a.timestamp(), a.model(), a.judgeModel(), a.datasetVersion(), a.packDatasetVersion(),
                "aaa", null, a.gitCommit(), a.experimentId(), a.configFingerprint(), a.configuration(),
                a.casesTotal(), a.casesPassed(), a.casesFailed(), a.casesSkipped(), a.casesError(),
                a.attemptsTotal(), a.attemptsPassed(), a.attemptsFailed(), a.attemptsSkipped(), a.attemptsError(),
                a.metrics(), a.cases(), a.durationMs(), a.qualityGate());
        EvalRun hashedB = new EvalRun(
                "b", a.timestamp(), a.model(), a.judgeModel(), a.datasetVersion(), a.packDatasetVersion(),
                "bbb", null, a.gitCommit(), a.experimentId(), a.configFingerprint(), a.configuration(),
                a.casesTotal(), a.casesPassed(), a.casesFailed(), a.casesSkipped(), a.casesError(),
                a.attemptsTotal(), a.attemptsPassed(), a.attemptsFailed(), a.attemptsSkipped(), a.attemptsError(),
                a.metrics(), a.cases(), a.durationMs(), a.qualityGate());
        ComparisonResult result = RunComparator.compare(hashedA, hashedB);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("datasetHash"));
    }

    @Test
    void missingDatasetHashVersusPresentIsInvalid() {
        EvalRun unhashed = run("a", "generation-v1", List.of(passing("1")));
        EvalRun hashed = new EvalRun(
                "b",
                unhashed.timestamp(),
                unhashed.model(),
                unhashed.judgeModel(),
                unhashed.datasetVersion(),
                unhashed.packDatasetVersion(),
                "aaa",
                null,
                unhashed.gitCommit(),
                unhashed.experimentId(),
                unhashed.configFingerprint(),
                unhashed.configuration(),
                unhashed.casesTotal(),
                unhashed.casesPassed(),
                unhashed.casesFailed(),
                unhashed.casesSkipped(),
                unhashed.casesError(),
                unhashed.attemptsTotal(),
                unhashed.attemptsPassed(),
                unhashed.attemptsFailed(),
                unhashed.attemptsSkipped(),
                unhashed.attemptsError(),
                unhashed.metrics(),
                unhashed.cases(),
                unhashed.durationMs(),
                unhashed.qualityGate());
        ComparisonResult missingBaseline = RunComparator.compare(unhashed, hashed);
        ComparisonResult missingCandidate = RunComparator.compare(hashed, unhashed);
        assertFalse(missingBaseline.valid());
        assertFalse(missingCandidate.valid());
        assertTrue(missingBaseline.invalidReason().contains("datasetHash"));
    }

    @Test
    void skippedVersusSkippedIsNotUnchangedFail() {
        CaseResult skip = new CaseResult(
                "red-1",
                EvalStatus.SKIPPED,
                Set.of(CaseKind.GENERATION),
                List.of(AttemptResult.skipped(1, "red")),
                null,
                null,
                eval.domain.RetrievalResult.notApplicable(),
                Rate.empty(),
                List.of(),
                0,
                Map.of());
        EvalRun a = run("a", "generation-v1", List.of(passing("ok"), skip));
        ComparisonResult result = RunComparator.compare(a, a);
        assertTrue(result.valid());
        assertEquals(1, result.unchangedPass());
        assertEquals(0, result.unchangedFail());
        assertEquals(CaseRegression.UNCHANGED_SKIPPED, find(result, "red-1"));
    }

    @Test
    void timeoutIsNotAQualityRegression() {
        EvalRun baseline = run("b", "generation-v1", List.of(passing("1"), passing("2")));
        EvalRun candidate = run("c", "generation-v1", List.of(erroring("1"), passing("2")));
        ComparisonResult result = RunComparator.compare(baseline, candidate, Thresholds.liveDelta());
        assertTrue(result.valid());
        assertEquals("NO_REGRESSION", result.decision());
        assertEquals(0, result.regressions());
        assertEquals(0, result.improvements());
        assertEquals(CaseRegression.NEW_ERROR, find(result, "1"));
        assertEquals(CaseRegression.UNCHANGED_PASS, find(result, "2"));
        assertEquals(0, result.mcnemar().n01());
        assertTrue(result.qualityGate().passed());
        assertTrue(result.qualityGate().rules().stream().noneMatch(r -> r.name().contains("criticalNewFailure")));
    }

    @Test
    void errorVersusErrorIsNotUnchangedFail() {
        EvalRun a = run("a", "generation-v1", List.of(erroring("1"), passing("2")));
        ComparisonResult result = RunComparator.compare(a, a);
        assertTrue(result.valid());
        assertEquals(0, result.regressions());
        assertEquals(0, result.unchangedFail());
        assertEquals(CaseRegression.UNCHANGED_ERROR, find(result, "1"));
        assertEquals(CaseRegression.UNCHANGED_PASS, find(result, "2"));
    }

    @Test
    void errorThenPassIsNotAQualityRecovery() {
        EvalRun baseline = run("b", "generation-v1", List.of(erroring("1"), failing("2")));
        EvalRun candidate = run("c", "generation-v1", List.of(passing("1"), failing("2")));
        ComparisonResult result = RunComparator.compare(baseline, candidate);
        assertTrue(result.valid());
        assertEquals(0, result.improvements());
        assertEquals(CaseRegression.INFRA_RESOLVED, find(result, "1"));
        assertEquals(CaseRegression.UNCHANGED_FAIL, find(result, "2"));
    }

    @Test
    void caseFailWithPerfectAttemptRateIsStillARegression() {
        CaseResult retrievalOverlayFail = new CaseResult(
                "GEN-001",
                EvalStatus.FAIL,
                Set.of(CaseKind.GENERATION, CaseKind.RETRIEVAL),
                List.of(new AttemptResult(
                        1,
                        EvalStatus.PASS,
                        ContractResult.pass(),
                        null,
                        "out",
                        null,
                        TokenUsage.unknown(),
                        5,
                        null,
                        null)),
                ContractResult.pass(),
                null,
                eval.domain.RetrievalResult.of(List.of("extra"), List.of("po-fluent")),
                Rate.of(1, 1),
                List.of(),
                5,
                Map.of());
        EvalRun baseline = run("b", "generation-v1", List.of(passing("GEN-001")));
        EvalRun candidate = run("c", "generation-v1", List.of(retrievalOverlayFail));
        ComparisonResult result = RunComparator.compare(baseline, candidate);
        assertTrue(result.valid());
        assertEquals(CaseRegression.NEW_FAILURE, find(result, "GEN-001"));
    }

    @Test
    void addedOrRemovedCaseFailsGate() {
        EvalRun baseline = run("b", "generation-v1", List.of(passing("A"), passing("B")));
        EvalRun candidate = run("c", "generation-v1", List.of(passing("A"), passing("C")));
        QualityGateResult gate = QualityGate.evaluate(candidate, Thresholds.liveDelta(), baseline);
        assertFalse(gate.passed());
        assertTrue(gate.rules().stream().anyMatch(r -> r.name().startsWith("removed.")));
        assertTrue(gate.rules().stream().anyMatch(r -> r.name().startsWith("added.")));
    }

    @Test
    void identicalCandidateHasNoRegression() {
        EvalRun a = run("a", "generation-v1", List.of(passing("1"), failing("2")));
        ComparisonResult result = RunComparator.compare(a, a);
        assertTrue(result.valid());
        assertEquals(0, result.regressions());
        assertEquals(0, result.improvements());
        assertEquals(1, result.unchangedPass());
        assertEquals(1, result.unchangedFail());
    }

    @Test
    void emptyDatasetIsNotOneHundredPercent() {
        EvalRun empty = run("e", "generation-v1", List.of());
        QualityGateResult gate = QualityGate.evaluate(empty, Thresholds.deterministicStrict(), null);
        assertFalse(gate.passed());
        assertTrue(!empty.metrics().overallPassRate().defined());
    }

    @Test
    void fixtureVsLiveIsInvalid() {
        EvalRun fixture = run("a", "generation-v1", List.of(passing("1")));
        EvalRun live = EvalRun.of(
                "b", "t", "m", null, "generation-v1", "pack-v1", "abc",
                new RunConfiguration("LIVE", "m", null, false, 1, false, "FAILURE", "build", "ollama"),
                1, 1, 0, 0, 0, 1, 1, 0, 0, fixture.metrics(), fixture.cases(), 10, null);
        ComparisonResult result = RunComparator.compare(fixture, live);
        assertFalse(result.valid());
        assertTrue(result.invalidReason().contains("mode"));
    }

    @Test
    void criticalNewFailureFailsGateEvenWhenOverallUnchanged() {
        CaseResult baseFail = failing("GEN-A");
        CaseResult basePass = passing("GEN-014");
        CaseResult candPass = passing("GEN-A");
        CaseResult candCrit = caseResult(
                "GEN-014",
                EvalStatus.FAIL,
                Set.of(CaseKind.GENERATION, CaseKind.HALLUCINATION),
                new eval.domain.ContractResult(
                        false,
                        List.of("cited missing API"),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        List.of(new eval.domain.Violation(
                                "GEN-014",
                                eval.domain.ViolationCategory.HALLUCINATION,
                                eval.domain.ViolationSeverity.CRITICAL,
                                "ContractGrader",
                                "cited missing API"))),
                null);
        EvalRun baseline = run("b", "generation-v1", List.of(baseFail, basePass));
        EvalRun candidate = run("c", "generation-v1", List.of(candPass, candCrit));
        QualityGateResult gate = QualityGate.evaluate(candidate, Thresholds.liveDelta(), baseline);
        assertFalse(gate.passed());
        assertTrue(gate.rules().stream().anyMatch(r -> r.name().contains("criticalNewFailure")));
    }

    @Test
    void dropWithinAllowedPassesGate() {
        EvalRun baseline = run("b", "generation-v1", nCases(100, 100));
        EvalRun candidate = run("c", "generation-v1", nCases(99, 100));
        Thresholds t = new Thresholds(null, null, null, null, null, null, null, null, null, 0.02);
        QualityGateResult gate = QualityGate.evaluate(candidate, t, baseline);
        assertTrue(gate.passed());
        assertTrue(gate.rules().stream().anyMatch(
                r -> "delta".equals(r.kind()) && r.passed() && r.detail().contains("candidate")));
    }

    private static EvalRun withHashes(EvalRun run, String datasetHash, String packHash) {
        return new EvalRun(
                run.runId(),
                run.timestamp(),
                run.model(),
                run.judgeModel(),
                run.datasetVersion(),
                run.packDatasetVersion(),
                datasetHash,
                packHash,
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
                run.cases(),
                run.durationMs(),
                run.qualityGate());
    }

    private static List<CaseResult> nCases(int pass, int total) {
        java.util.ArrayList<CaseResult> out = new java.util.ArrayList<>();
        for (int i = 1; i <= total; i++) {
            out.add(i <= pass ? passing("c" + i) : failing("c" + i));
        }
        return out;
    }

    private static CaseRegression find(ComparisonResult result, String id) {
        return result.cases().stream().filter(c -> id.equals(c.caseId())).findFirst().orElseThrow().regression();
    }

    private static EvalRun run(String id, String dataset, List<CaseResult> cases) {
        return run(id, dataset, cases, 1, false);
    }

    private static EvalRun run(
            String id, String dataset, List<CaseResult> cases, int repetitions, boolean includeRed) {
        EvalMetrics metrics = MetricsAggregator.aggregate(cases, null);
        int passed = (int) cases.stream().filter(c -> c.status() == EvalStatus.PASS).count();
        int failed = (int) cases.stream().filter(c -> c.status() == EvalStatus.FAIL).count();
        return EvalRun.of(
                id,
                "t",
                "model-" + id,
                null,
                dataset,
                "pack-v1",
                "abc",
                new RunConfiguration(
                        "DETERMINISTIC",
                        "model-" + id,
                        null,
                        false,
                        repetitions,
                        includeRed,
                        "FAILURE",
                        "build",
                        "ollama"),
                cases.size(),
                passed,
                failed,
                0,
                0,
                cases.size(),
                passed,
                failed,
                0,
                metrics,
                cases,
                10,
                null);
    }

    private static CaseResult passing(String id) {
        return caseResult(id, EvalStatus.PASS, Set.of(CaseKind.GENERATION), ContractResult.pass(), null);
    }

    private static CaseResult passingWithJudge(String id, JudgeDecision decision) {
        return caseResult(
                id,
                EvalStatus.PASS,
                Set.of(CaseKind.GENERATION),
                ContractResult.pass(),
                new JudgeResult(decision, 0.5, List.of(), true, decision.name()));
    }

    private static CaseResult failing(String id) {
        return caseResult(id, EvalStatus.FAIL, Set.of(CaseKind.GENERATION), ContractResult.fail(List.of("x")), null);
    }

    private static CaseResult erroring(String id) {
        AttemptResult attempt = AttemptResult.error(1, "TIMEOUT", "Ollama timeout", 5);
        return new CaseResult(
                id,
                EvalStatus.ERROR,
                Set.of(CaseKind.GENERATION),
                List.of(attempt),
                null,
                null,
                eval.domain.RetrievalResult.notApplicable(),
                Rate.empty(),
                List.of("TIMEOUT: Ollama timeout"),
                5,
                Map.of());
    }

    private static CaseResult hallucinationFail(String id) {
        return caseResult(
                id,
                EvalStatus.FAIL,
                Set.of(CaseKind.GENERATION, CaseKind.HALLUCINATION, CaseKind.NEGATIVE),
                ContractResult.fail(List.of("Invalid password")),
                null);
    }

    private static CaseResult caseResult(
            String id,
            EvalStatus status,
            Set<CaseKind> kinds,
            ContractResult contract,
            JudgeResult judge) {
        AttemptResult attempt = new AttemptResult(
                1,
                status,
                contract,
                judge,
                "out",
                judge == null ? null : judge.raw(),
                TokenUsage.unknown(),
                5,
                null,
                status == EvalStatus.FAIL ? "x" : null);
        Rate success = status == EvalStatus.PASS ? Rate.of(1, 1) : Rate.of(0, 1);
        return new CaseResult(
                id, status, kinds, List.of(attempt), contract, null,
                eval.domain.RetrievalResult.notApplicable(), success, List.of(), 5, Map.of());
    }
}
