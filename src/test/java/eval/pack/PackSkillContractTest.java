package eval.pack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("pack")
@DisplayName("Pack skill and rules")
class PackSkillContractTest {

    @Test
    @DisplayName("qa-write-test keeps lab-36 and refuse anchors")
    void skillAnchors() {
        String skill = PackFiles.read("qa-write-test.md");
        assertTrue(skill.contains("submitExpectingError"), "skill lost form-negative chain");
        assertTrue(skill.contains("Wrong login or password"), "skill lost canon message");
        assertTrue(skill.contains("Отказ."), "skill lost refuse token");
        assertTrue(skill.contains("@Step"), "skill must forbid @Step on *Tests");
        assertTrue(skill.contains("*Tests"), "skill must name *Tests as the @Step ban");
        assertTrue(skill.contains("формулировка пользователя"), "skill must not copy error text from the user");
        assertTrue(skill.contains("Не отказ"), "skill must not refuse a locator-in-test request");
        assertTrue(skill.contains("два слоя") || skill.contains("Два слоя"), "skill lost mixed-layer refuse");
    }

    @Test
    @DisplayName("rules keep jailbreak and RAG diet surface")
    void rulesAnchors() {
        String rules = PackFiles.read("rules.md");
        assertTrue(rules.toLowerCase().contains("не игнорируй") || rules.contains("Rules ON"), rules);
        assertTrue(rules.contains("git commit"), "rules lost commit ban");
        assertTrue(rules.contains("testE2e"), "rules lost testE2e ban");
        assertTrue(rules.contains("весь rag") || rules.contains("весь RAG"), "rules lost full-rag refuse");
        assertTrue(rules.contains("два слоя") || rules.contains("Два слоя"), "rules lost mixed-layer refuse");
    }

    @Test
    @DisplayName("po-step forbids steps in *Tests (that wording was a live false green)")
    void poStepDoesNotInviteStepsInTests() {
        String chunk = PackFiles.rag("po-step");
        assertTrue(chunk.contains("*Tests"), "po-step must name *Tests");
        assertTrue(chunk.contains("Don't") || chunk.contains("не ставь"), chunk);
        assertFalse(
                chunk.contains("или `Allure.step` в тесте"),
                "po-step invited Allure.step in the test — 7b put @Step on the method");
    }

    @Test
    @DisplayName("login PO context keeps HomePage vs LoginPage split")
    void loginPoContext() {
        String ctx = PackFiles.read("context/login-po.md");
        assertTrue(ctx.contains("fillAndSubmitForm"), ctx);
        assertTrue(ctx.contains("HomePage"), ctx);
        assertTrue(ctx.contains("submitExpectingError"), ctx);
        assertTrue(ctx.contains("LoginPage"), ctx);
    }
}
