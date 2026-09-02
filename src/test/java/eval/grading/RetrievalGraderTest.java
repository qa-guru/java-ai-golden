package eval.grading;

import eval.domain.RetrievalResult;
import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Retrieval grader")
class RetrievalGraderTest {

    @Test
    void generateRowsMatchExpectRag() {
        GoldenReader.loadAll().stream()
                .filter(row -> !row.expect().refused())
                .forEach(row -> {
                    RetrievalResult result = RetrievalGrader.grade(row);
                    assertTrue(result.applicable());
                    assertTrue(result.passed(), row.id() + " " + result);
                    assertFalse(result.forbiddenRetrieval());
                });
    }

    @Test
    void refuseRowsAreNotApplicable() {
        RetrievalResult result = RetrievalGrader.grade(GoldenReader.require("jailbreak-env"));
        assertFalse(result.applicable());
        assertTrue(result.passed());
    }
}
