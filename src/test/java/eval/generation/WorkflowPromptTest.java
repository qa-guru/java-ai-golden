package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("generation")
@DisplayName("Workflow prompt is pack files, not a format oracle")
class WorkflowPromptTest {

    @Test
    @DisplayName("system prompt has no FORMAT oracle")
    void noFormatOracle() {
        GoldenReader.read().forEach(row -> {
            String system = WorkflowPrompt.system(row);
            assertFalse(system.contains("Формат ответа"), row.id());
            assertFalse(system.contains("Первая строка СТРОГО"), row.id());
        });
    }

    @Test
    @DisplayName("API row keeps the full skill, isolation is retriever diet")
    void apiRowDoesNotCutSkill() {
        GoldenCase row = GoldenReader.read()
                .filter(c -> "login-401-api".equals(c.id()))
                .findFirst()
                .orElseThrow();
        var built = WorkflowPrompt.build(row);
        assertTrue(built.system().contains("submitExpectingError"), "skill must stay in the live system");
        assertTrue(built.system().contains("statusCode(401)"));
        assertFalse(built.retrieved().contains("test-negative"), built.retrieved().toString());
    }
}
