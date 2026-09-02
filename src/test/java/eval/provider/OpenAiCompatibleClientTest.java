package eval.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("OpenAI-compatible parser")
class OpenAiCompatibleClientTest {

    @Test
    void parsesContentAndUsageWithoutInventingCost() throws Exception {
        ModelResponse response = OpenAiCompatibleClient.parse(
                """
                {"choices":[{"message":{"content":"RAG: x"}}],"usage":{"prompt_tokens":11,"completion_tokens":7}}
                """,
                42);
        assertEquals("RAG: x", response.content());
        assertEquals(11, response.tokens().inputTokens());
        assertEquals(7, response.tokens().outputTokens());
        assertEquals(18, response.tokens().totalTokens());
        assertNull(response.tokens().estimatedCost());
        assertEquals(42, response.durationMs());
    }

    @Test
    void http429IsRateLimit() {
        assertEquals(EvalInfrastructureException.RATE_LIMIT, EvalInfrastructureException.httpKind(429));
        assertEquals(EvalInfrastructureException.HTTP_ERROR, EvalInfrastructureException.httpKind(500));
        assertEquals(EvalInfrastructureException.HTTP_ERROR, EvalInfrastructureException.httpKind(404));
    }

    @Test
    void emptyChoicesIsInfrastructure() {
        EvalInfrastructureException err = assertThrows(
                EvalInfrastructureException.class,
                () -> OpenAiCompatibleClient.parse("{\"choices\":[]}", 1));
        assertEquals(EvalInfrastructureException.EMPTY_RESPONSE, err.kind());
    }

    @Test
    void malformedJsonIsInfrastructure() {
        EvalInfrastructureException err = assertThrows(
                EvalInfrastructureException.class,
                () -> OpenAiCompatibleClient.parse("{not json}", 1));
        assertEquals(EvalInfrastructureException.PARSER_ERROR, err.kind());
    }

    @Test
    void errorObjectIsInfrastructure() {
        EvalInfrastructureException err = assertThrows(
                EvalInfrastructureException.class,
                () -> OpenAiCompatibleClient.parse(
                        "{\"error\":{\"message\":\"model not found\"},\"choices\":[]}", 1));
        assertTrue(err.getMessage().contains("model not found"));
    }

    @Test
    void unknownProviderIsRejected() {
        eval.execution.EvalConfig config = eval.execution.EvalConfig.resolve(new String[]{
                "--mode=deterministic",
                "--provider=cursor"
        });
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class, () -> ModelRunners.create(config));
        assertTrue(err.getMessage().contains("cursor"));
    }

    @Test
    void millLiveModeDefaultsToOllamaRunner() {
        eval.execution.EvalConfig config = eval.execution.EvalConfig.resolve(new String[]{
                "--mode=live"
        });
        assertTrue(ModelRunners.create(config) instanceof eval.generation.OllamaClient);
    }
}
