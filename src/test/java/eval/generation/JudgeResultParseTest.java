package eval.generation;

import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Judge structured parse")
class JudgeResultParseTest {

    @Test
    void jsonAccept() {
        JudgeResult result = Judge.parseResult("""
                {"decision":"ACCEPT","score":0.92,"reasons":["layer ok","canon message"]}
                """);
        assertTrue(result.schemaValid());
        assertEquals(JudgeDecision.ACCEPT, result.decision());
        assertEquals(0.92, result.score());
        assertEquals(2, result.reasons().size());
    }

    @Test
    void jsonRejectPreferredOverSurroundingProse() {
        JudgeResult result = Judge.parseResult("""
                Looks good, I think...
                {"decision":"REJECT","score":0.1,"reasons":["hallucinated Invalid password"]}
                """);
        assertEquals(JudgeDecision.REJECT, result.decision());
        assertTrue(result.schemaValid());
    }

    @Test
    void malformedJsonFallsBackToVerdictLine() {
        JudgeResult result = Judge.parseResult("""
                VERDICT: ПРИНЯТО
                {not json}
                """);
        assertEquals(JudgeDecision.ACCEPT, result.decision());
        assertFalse(result.schemaValid());
    }

    @Test
    void scoreOutOfRangeIsInvalidThenVerdictFallback() {
        JudgeResult result = Judge.parseResult("""
                VERDICT: НЕ ПРИНЯТО
                {"decision":"ACCEPT","score":1.5,"reasons":[]}
                """);
        assertEquals(JudgeDecision.REJECT, result.decision());
    }

    @Test
    void freeTextWithoutVerdictIsPending() {
        JudgeResult result = Judge.parseResult("Looks good, I think the test is fine.");
        assertEquals(JudgeDecision.PENDING, result.decision());
    }

    @Test
    void millParseStillUsesVerdictLineWhenJsonInvalid() {
        assertEquals(Judge.Verdict.ACCEPTED, Judge.parse("VERDICT: ПРИНЯТО\n{broken"));
        assertEquals(Judge.Verdict.REJECTED, Judge.parse("VERDICT: НЕ ПРИНЯТО"));
        assertEquals(Judge.Verdict.PENDING, Judge.parse("Looks good, I think..."));
    }
}
