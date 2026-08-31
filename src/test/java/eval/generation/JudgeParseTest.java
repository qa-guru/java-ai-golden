package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("eval")
@Tag("generation")
@DisplayName("Judge parse and mode")
class JudgeParseTest {

    @Test
    void acceptedOnFirstLine() {
        assertEquals(Judge.Verdict.ACCEPTED, Judge.parse("VERDICT: ПРИНЯТО\nтаблица"));
    }

    @Test
    void rejectedBeforeAcceptedSubstring() {
        assertEquals(Judge.Verdict.REJECTED, Judge.parse("VERDICT: НЕ ПРИНЯТО"));
    }

    @Test
    void pendingWithoutVerdictLine() {
        assertEquals(Judge.Verdict.PENDING, Judge.parse("выглядит хорошо, Unauthorized ок"));
    }

    @Test
    void formNegativeMode() {
        GoldenCase row = new GoldenCase(
                "login-wrong-password-e2e",
                "p",
                new GoldenCase.Expect(
                        "e2e",
                        "LoginTests",
                        null,
                        List.of(),
                        false,
                        List.of("submitExpectingError"),
                        null),
                List.of());
        assertEquals("form-negative", Judge.mode(row));
    }

    @Test
    void formHappyDoesNotUseFormNegativeMode() {
        GoldenCase row = new GoldenCase(
                "login-valid-e2e",
                "p",
                new GoldenCase.Expect(
                        "e2e",
                        "LoginTests",
                        null,
                        List.of(),
                        false,
                        List.of("fillAndSubmitForm", "shouldHaveWelcomeMessage"),
                        null),
                List.of());
        assertEquals("form-happy", Judge.mode(row));
    }

    @Test
    void apiMode() {
        GoldenCase row = new GoldenCase(
                "login-401-api",
                "p",
                new GoldenCase.Expect(
                        "api",
                        "AuthApiTests",
                        401,
                        List.of(),
                        false,
                        List.of("statusCode(401)"),
                        null),
                List.of());
        assertEquals("api-negative", Judge.mode(row));
    }
}
