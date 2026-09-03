package eval.reporting;

import eval.cli.EvalMain;
import eval.cli.ExitCode;
import eval.domain.AttemptResult;
import eval.domain.CaseResult;
import eval.domain.ContractResult;
import eval.domain.EvalMetrics;
import eval.domain.EvalRun;
import eval.domain.EvalStatus;
import eval.domain.Rate;
import eval.domain.TokenUsage;
import eval.execution.EvalConfig;
import eval.execution.EvalExecutor;
import eval.metrics.MetricsAggregator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Run JSON integrity at the load boundary")
class ReportIntegrityTest {

    @TempDir
    Path tmp;

    @Test
    void honestNewFailureStillExitsQualityGateFailed() {
        EvalRun baseline = deterministic();
        Path base = tmp.resolve("baseline.json");
        Path candidate = tmp.resolve("candidate.json");
        ReportIo.writeJson(base, baseline);
        ReportIo.writeJson(candidate, withFirstCaseFailed(baseline));
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--gate",
                "--baseline=" + base,
                "--candidate=" + candidate,
                "--output=" + tmp.resolve("out"),
                "--artifacts=never"
        });
        assertEquals(ExitCode.QUALITY_GATE_FAILED, code);
    }

    @Test
    void forgedMetricsVersusFailCaseIsNotSuccess() {
        EvalRun baseline = deterministic();
        EvalRun honestFail = withFirstCaseFailed(baseline);
        EvalRun forged = new EvalRun(
                honestFail.runId(),
                honestFail.timestamp(),
                honestFail.model(),
                honestFail.judgeModel(),
                honestFail.datasetVersion(),
                honestFail.packDatasetVersion(),
                honestFail.datasetHash(),
                honestFail.packHash(),
                honestFail.gitCommit(),
                honestFail.experimentId(),
                honestFail.configFingerprint(),
                honestFail.configuration(),
                baseline.casesTotal(),
                baseline.casesPassed(),
                baseline.casesFailed(),
                baseline.casesSkipped(),
                baseline.casesError(),
                baseline.attemptsTotal(),
                baseline.attemptsPassed(),
                baseline.attemptsFailed(),
                baseline.attemptsSkipped(),
                baseline.attemptsError(),
                baseline.metrics(),
                honestFail.cases(),
                honestFail.durationMs(),
                honestFail.qualityGate());
        Path cand = tmp.resolve("forged.json");
        ReportIo.writeJson(cand, forged);
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--gate",
                "--baseline=baselines/generation-v1.json",
                "--candidate=" + cand,
                "--output=" + tmp.resolve("out"),
                "--artifacts=never"
        });
        assertNotEquals(ExitCode.SUCCESS, code);
        assertEquals(ExitCode.USAGE, code);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> ReportIo.readRun(cand));
        assertTrue(ex.getMessage().contains("overallPassRate"));
    }

    @Test
    void truncatedRunJsonIsUsageNotNpe() {
        Path trunc = tmp.resolve("trunc.json");
        try {
            Files.writeString(trunc, "{\"runId\":\"x\"}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--gate",
                "--baseline=baselines/generation-v1.json",
                "--candidate=" + trunc,
                "--output=" + tmp.resolve("out"),
                "--artifacts=never"
        });
        assertEquals(ExitCode.USAGE, code);
    }

    @Test
    void truncatedJsonThrowsNamedMismatchNotNpe() {
        Path trunc = tmp.resolve("trunc.json");
        try {
            Files.writeString(trunc, "{\"runId\":\"x\"}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> ReportIo.readRun(trunc));
        assertTrue(ex.getMessage().contains("INVALID RUN"));
        assertTrue(ex.getMessage().contains("metrics"));
    }

    @Test
    void malformedJsonIsUsage() {
        Path bad = tmp.resolve("bad.json");
        try {
            Files.writeString(bad, "{\"runId\":\"x\"");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int code = EvalMain.run(new String[]{
                "--mode=regression",
                "--baseline=baselines/generation-v1.json",
                "--candidate=" + bad,
                "--output=" + tmp.resolve("out"),
                "--artifacts=never"
        });
        assertEquals(ExitCode.USAGE, code);
    }

    private static EvalRun deterministic() {
        return new EvalExecutor(EvalConfig.resolve(new String[]{
                "--mode=deterministic",
                "--artifacts=never",
                "--output=build/eval-integrity"
        })).execute();
    }

    private static EvalRun withFirstCaseFailed(EvalRun src) {
        List<CaseResult> cases = new ArrayList<>(src.cases());
        CaseResult first = cases.getFirst();
        ContractResult fail = ContractResult.fail(List.of("forced"));
        AttemptResult attempt = new AttemptResult(
                1, EvalStatus.FAIL, fail, null, "out", null, TokenUsage.unknown(), 1, null, "forced");
        cases.set(0, new CaseResult(
                first.caseId(),
                EvalStatus.FAIL,
                first.kinds(),
                List.of(attempt),
                fail,
                null,
                first.retrieval(),
                Rate.of(0, 1),
                List.of(),
                1,
                first.metadata(),
                fail.taxonomy(),
                null));
        EvalMetrics metrics = MetricsAggregator.aggregate(cases, null);
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
                    case SKIPPED -> attemptsSkipped++;
                    case ERROR -> attemptsError++;
                }
            }
        }
        EvalRun rebuilt = new EvalRun(
                src.runId() + "-fail",
                src.timestamp(),
                src.model(),
                src.judgeModel(),
                src.datasetVersion(),
                src.packDatasetVersion(),
                src.datasetHash(),
                src.packHash(),
                src.gitCommit(),
                src.experimentId(),
                src.configFingerprint(),
                src.configuration(),
                cases.size(),
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
                src.durationMs(),
                null);
        rebuilt.requireIntegrity();
        return rebuilt;
    }
}
