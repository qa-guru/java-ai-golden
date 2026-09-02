package eval.generation;

import eval.cli.EvalMain;
import eval.cli.ExitCode;
import eval.domain.JudgeDecision;
import eval.domain.Rate;
import eval.domain.TokenUsage;
import eval.provider.ModelResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Judge calibration and consistency")
class JudgeCalibrationTest {

    @Test
    void calibrationCorpusComputesConfusionAndDoesNotClaimPublishedAccuracy() {
        JudgeCalibration.Report report = JudgeCalibration.evaluate(JudgeCalibration.load(JudgeCalibration.defaultFile()));
        assertEquals(20, report.n());
        assertEquals(10, report.confusion().trueAccept());
        assertEquals(2, report.confusion().falseAccept());
        assertEquals(1, report.confusion().falseReject());
        assertEquals(7, report.confusion().trueReject());
        assertEquals(17, report.accuracy().hits());
        assertEquals(20, report.accuracy().total());
        assertEquals(10, report.precision().hits());
        assertEquals(12, report.precision().total());
        assertEquals(10, report.recall().hits());
        assertEquals(11, report.recall().total());
        assertTrue(report.f1() != null && report.f1() > 0.8 && report.f1() < 1.0);
        assertTrue(report.caveat().contains("too small"));
        assertFalse(report.live());
        for (JudgeCalibration.LabeledCase row : JudgeCalibration.load(JudgeCalibration.defaultFile())) {
            if ("REJECT".equalsIgnoreCase(row.humanExpectedDecision())) {
                assertTrue(row.candidate() != null && !row.candidate().isBlank(), row.id());
            }
        }
    }

    @Test
    void evaluateLiveUsesEmbeddedCandidateForRejectRows() throws Exception {
        List<JudgeCalibration.LabeledCase> rows = List.of(
                new JudgeCalibration.LabeledCase(
                        "l1", "login-wrong-password-e2e", "ACCEPT", null, "ok", null),
                new JudgeCalibration.LabeledCase(
                        "l2",
                        "login-wrong-password-e2e",
                        "REJECT",
                        null,
                        "bad",
                        "CALIBRATION_REJECT_MARKER fillAndSubmitForm(\"u\",\"p\")"));
        JudgeCalibration.Report report = JudgeCalibration.evaluateLive(
                rows,
                (sys, user, model) -> new ModelResponse(
                        user.contains("CALIBRATION_REJECT_MARKER")
                                ? "VERDICT: НЕ ПРИНЯТО"
                                : "VERDICT: ПРИНЯТО",
                        TokenUsage.unknown(),
                        1),
                "stub");
        assertTrue(report.live());
        assertEquals(2, report.n());
        assertEquals(1.0, report.accuracy().value(), 1e-12);
        assertEquals(1, report.confusion().trueAccept());
        assertEquals(1, report.confusion().trueReject());
    }

    @Test
    void calibrateJudgeCliWritesReportAndDoesNotFailTheMillGate(@TempDir Path tmp) {
        int code = EvalMain.run(new String[]{
                "--calibrate-judge",
                "--output=" + tmp,
                "--artifacts=never"
        });
        assertEquals(ExitCode.SUCCESS, code);
        assertTrue(Files.isRegularFile(tmp.resolve("calibration").resolve("report.json")));
        assertTrue(Files.isRegularFile(tmp.resolve("calibration").resolve("report.md")));
    }

    @Test
    void judgeConsistencyIsMajorityOverRepetitions() {
        Rate r = JudgeConsistency.stability(List.of(
                JudgeDecision.ACCEPT, JudgeDecision.REJECT, JudgeDecision.ACCEPT));
        assertEquals(2, r.hits());
        assertEquals(3, r.total());
        assertEquals(2.0 / 3.0, r.value(), 1e-12);
    }
}
