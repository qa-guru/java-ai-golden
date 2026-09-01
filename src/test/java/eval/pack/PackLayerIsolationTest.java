package eval.pack;

import eval.generation.GoldenCase;
import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("pack")
@DisplayName("Pack layer isolation")
class PackLayerIsolationTest {

    @Test
    @DisplayName("API retrieve diet has JSON canon and no form-negative chain")
    void apiDietDoesNotLeakFormNegative() {
        String diet = PackFiles.diet(LexicalRetriever.retrieve(row("login-401-api").prompt()));
        assertTrue(diet.contains("Wrong login or password"), "API diet missing canon message");
        assertTrue(diet.contains("statusCode(401)") || diet.contains("401"), "API diet missing 401");
        assertFalse(
                diet.contains("submitExpectingError"),
                "API retrieve diet leaked submitExpectingError");
        assertFalse(diet.contains("fillAndSubmitForm"), "API retrieve diet leaked fillAndSubmitForm");
    }

    @Test
    @DisplayName("form-negative retrieve diet contains the lab-36 chain")
    void formNegativeDietHasExpectingError() {
        String diet = PackFiles.diet(LexicalRetriever.retrieve(row("login-wrong-password-e2e").prompt()));
        assertTrue(diet.contains("submitExpectingError"), "UI diet missing submitExpectingError");
        assertTrue(diet.contains("Wrong login or password"), "UI diet missing canon message");
        assertTrue(diet.contains("po-step") || diet.contains("@Step"), "UI diet missing po-step");
    }

    @Test
    @DisplayName("happy-path retrieve diet has fillAndSubmitForm and not submitExpectingError")
    void happyDietDoesNotLeakFormNegative() {
        String diet = PackFiles.diet(LexicalRetriever.retrieve(row("login-valid-e2e").prompt()));
        assertTrue(diet.contains("fillAndSubmitForm"), "happy diet missing fillAndSubmitForm");
        assertFalse(
                diet.contains("submitExpectingError"),
                "happy retrieve diet leaked submitExpectingError");
    }

    private static GoldenCase row(String id) {
        return GoldenReader.read()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing golden " + id));
    }
}
