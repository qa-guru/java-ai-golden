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
    void packVersionMismatchIsInvalid() {
        EvalRun a = run("a", "generation-v1", List.of(passing("GEN-001")));
        EvalRun b = new EvalRun(
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
        assertEquals(CaseRegression.STILL_PASSING, find(result, "GEN-001"));
        assertEquals(CaseRegression.STILL_FAILING, find(result, "GEN-002"));
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
    void deltaRegressionOfThreePointsFailsWhenAllowedIsTwo() {
        EvalRun baseline = run("b", "generation-v1", List.of(
                passing("1"), passing("2"), passing("3"), passing("4"), passing("5"),
                passing("6"), passing("7"), passing("8"), passing("9"), passing("10"),
                passing("11"), passing("12"), passing("13"), passing("14"), passing("15"),
                passing("16"), passing("17"), passing("18"), passing("19"), failing("20")));
        EvalRun candidate = run("c", "generation-v1", List.of(
                passing("1"), passing("2"), passing("3"), passing("4"), passing("5"),
                passing("6"), passing("7"), passing("8"), passing("9"), passing("10"),
                passing("11"), passing("12"), passing("13"), passing("14"), passing("15"),
                passing("16"), passing("17"), failing("18"), failing("19"), failing("20")));
        Thresholds t = new Thresholds(null, null, null, null, null, null, null, null, null, 0.02);
        QualityGateResult gate = QualityGate.evaluate(candidate, t, baseline);
        assertFalse(gate.passed());
        assertTrue(gate.rules().stream().anyMatch(r -> "delta".equals(r.kind()) && !r.passed()));
    }

    private static CaseRegression find(ComparisonResult result, String id) {
        return result.cases().stream().filter(c -> id.equals(c.caseId())).findFirst().orElseThrow().regression();
    }

    private static EvalRun run(String id, String dataset, List<CaseResult> cases) {
        EvalMetrics metrics = MetricsAggregator.aggregate(cases, null);
        int passed = (int) cases.stream().filter(c -> c.status() == EvalStatus.PASS).count();
        int failed = (int) cases.stream().filter(c -> c.status() == EvalStatus.FAIL).count();
        return new EvalRun(
                id,
                "t",
                "model-" + id,
                null,
                dataset,
                "pack-v1",
                "abc",
                new RunConfiguration("DETERMINISTIC", "model-" + id, null, false, 1, false, "FAILURE", "build", "ollama"),
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
