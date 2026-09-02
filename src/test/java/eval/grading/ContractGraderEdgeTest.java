package eval.grading;

import eval.domain.ContractResult;
import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;
import eval.generation.GoldenCase;
import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Contract grader edge cases")
class ContractGraderEdgeTest {

    @Test
    void emptyOutputFailsGenerateCase() {
        ContractResult result = ContractGrader.grade(row("login-wrong-password-e2e"), "");
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("RAG:")));
    }

    @Test
    void nullOutputTreatedAsEmpty() {
        ContractResult result = ContractGrader.grade(row("login-401-api"), null);
        assertFalse(result.passed());
    }

    @Test
    void malformedRagHeader() {
        String out = "not a header\nclass AuthApiTests {}";
        ContractResult result = ContractGrader.grade(row("login-401-api"), out);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("RAG:")));
    }

    @Test
    void partialOutputMissingClass() {
        String out = """
                RAG: test-api-layer, test-layers

                @Layer("api")
                """;
        ContractResult result = ContractGrader.grade(row("login-401-api"), out);
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("class")));
    }

    @Test
    void multipleViolationsAreAllReported() {
        String out = """
                RAG: test-negative

                @Layer("api")
                class Wrong {
                    void t() { statusCode(401); }
                }
                """;
        ContractResult result = ContractGrader.grade(row("login-wrong-password-e2e"), out);
        assertFalse(result.passed());
        assertTrue(result.violations().size() >= 2, result.violations().toString());
    }

    @Test
    void unicodeInMustNotStillMatches() {
        GoldenCase row = row("jailbreak-env");
        ContractResult result = ContractGrader.grade(row, "Отказ. Не коммичу.");
        assertTrue(result.passed(), result.violations().toString());
    }

    @Test
    void hugeOutputStillGrades() {
        StringBuilder sb = new StringBuilder();
        sb.append("RAG: test-negative, po-locators, po-step, cfg-stands\n\n");
        sb.append("@Layer(\"e2e\")\nclass LoginTests {\n");
        sb.append("void t() { loginPage.submitExpectingError().shouldHaveErrorMessage(\"Wrong login or password\"); }\n");
        sb.append("}\n");
        sb.append("x".repeat(200_000));
        ContractResult result = ContractGrader.grade(row("login-wrong-password-e2e"), sb.toString());
        assertTrue(result.passed(), result.violations().toString());
    }

    @Test
    void conflictingRefuseAndJava() {
        ContractResult result = ContractGrader.grade(
                row("mixed-layer"),
                "Отказ.\n\nclass LoginTests { void t() {} }\n");
        assertFalse(result.passed());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Java")));
    }

    @Test
    void unexpectedRagSpacingStillParsesIds() {
        String fixture = GoldenReader.fixture("login-401-api");
        String spaced = fixture.replace("RAG: test-api-layer, test-layers", "RAG:  test-api-layer ,  test-layers  ");
        ContractResult result = ContractGrader.grade(row("login-401-api"), spaced);
        assertTrue(result.passed(), result.violations().toString());
    }

    @Test
    void judgeAcceptCannotOverrideContractFail() {
        ContractResult contract = ContractGrader.grade(row("jailbreak-env"), "Looks fine to me.");
        assertFalse(contract.passed());
        JudgeResult judge = new JudgeResult(JudgeDecision.ACCEPT, 0.99, List.of("looks good"), true, "{}");
        assertTrue(HardSoftPolicy.judgeOverrideAttempted(contract, judge));
        assertEqualsFail(HardSoftPolicy.hardStatus(contract));
    }

    private static void assertEqualsFail(eval.domain.EvalStatus status) {
        org.junit.jupiter.api.Assertions.assertEquals(eval.domain.EvalStatus.FAIL, status);
    }

    private static GoldenCase row(String id) {
        return GoldenReader.require(id);
    }
}
